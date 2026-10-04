# Loading: single pass or an intermediate Parquet file

TODO item 4: investigate options for single pass parsing, or parsing into an
intermediate Parquet file. Measured on 2026-10-04 at commit `ad2e55f`, on an
8 core machine with 31 GB of RAM, using JDK 27-ea.

## How loading worked before option B

1. **Pass 1** parses every file and writes each value to a temp dictionary:
   IRIs into one text file per namespace, literals into one text file per
   datatype or language, and big integers into an in-memory set. Each file is
   then sorted and written as a Parquet dictionary. A value's id is its row
   number in that sorted file. If a namespace's local names are all a prefix
   followed by a number, it becomes a bitmap instead, and the id is computed
   from the string.
2. **Pass 2** parses every file again and looks up the id of each subject and
   object (predicates are cached). It then appends the ids to the triple list
   for that predicate and value type.

## Measurements

| What | Cost |
| --- | --- |
| Parsing the taxonomy RDF/XML (55.9M triples, 4.3 GB) | 153 s, about 365k triples/s |
| Building a 2M entry IRI dictionary (sort and write) | 35 s |
| Value to id lookup, 2M entry dictionary, old 1M row groups | 45 ms |
| Id to string, same dictionary, old 1M row groups | 22 ms |
| Value to id lookup, 4096 row groups (now) | 1.3 ms |
| Id to string, 4096 row groups (now) | 0.7 ms |
| Value to id lookup, 1024 row groups | 1.35 ms (no better) |

hardwood writes no page index, so statistics can only skip whole row groups.
Since `ad2e55f` the dictionaries use 4096-row groups. What is left is a fixed
cost of about 1 ms per lookup for building the column readers and handing the
work to the decode thread pool. Smaller row groups do not reduce it.

## What this means

**The second parse is not the problem; the lookups in pass 2 are.** Parsing
again costs as much as the first pass, 153 s for the taxonomy. Pass 2 also
does one dictionary lookup for every subject and object that is not in a
bitmap namespace:

- Taxonomy: about 6.6M literal objects at roughly 1 ms each is close to
  2 hours. The taxon IRIs are numbered, so they resolve through the bitmaps
  in microseconds.
- UniProt size (more than 20 billion triples, per the README): at 1 ms per
  lookup this is out of reach, whatever the number of passes.

An intermediate Parquet file on its own (option A below) would therefore save
at most the 153 s of the second parse, and leave the hours of lookups in
place. A useful change has to stop resolving terms one at a time against
Parquet.

## Options

### A. Stage the parsed triples in Parquet, then resolve as now

Pass 1 writes every triple as strings (plus term kinds) to a staging Parquet
file while it fills the temp dictionaries. Pass 2 reads the staging file
instead of parsing the RDF again.

- Saves: the second parse, about half of the parsing cost.
- Keeps: the per-term lookups, which are the real cost.
- Extra: a staging file about as large as the data, compressed.

Worth having only as a building block for B or C.

### B. Provisional ids in one pass, then remap

Pass 1 gives every new term a provisional id in the order it is first seen,
from an in-memory map from term to provisional id per dictionary. It writes
the triples as provisional ids straight into staging Parquet, and the term
strings to the temp dictionaries as now. When a dictionary is sorted, its
order gives an array from provisional id to final id. A final sequential
pass rewrites the staged triples through those arrays (an array index, not a
lookup) into the per-predicate lists. Bitmap namespaces keep computing their
id from the string, so they need no map.

- One parse. Every step after it is sequential reads and writes.
- Memory: the term-to-id map holds every distinct non-bitmap term, roughly
  80 to 100 bytes plus the string per term. Fine for the taxonomy, a few
  million terms. Not for UniProt-size data with billions of distinct terms.
- Moderate change: the temp dictionaries and the pass-2 handler change,
  and the readers do not.

### C. External sort and merge join (as Jena's xloader and HDT do)

Pass 1 stages triples with their term strings in Parquet. The distinct terms
of each dictionary come from an external sort (sorted Parquet runs, merged),
which also assigns the ids. The staged triples are then sorted by subject
string and merge joined with the sorted dictionary, then again by object
string, giving id triples without a single random lookup.

- Memory independent of data size; all I/O sequential.
- Disk: a few times the size of the data during the sorts.
- The most work: an external sort over Parquet runs is new code. hardwood
  reads and writes the runs, the merge is ours.

### D. Hash ids instead of provisional ids

As B, but the provisional id is a 64-bit hash of the term, so no map is
needed. The remap from hash to final id then needs a sort-merge join, as in
C. Collisions have to be detected when the dictionary is sorted (equal hash,
different string) and handled by a slower fallback. It is cheaper than C, but
still needs C's sort machinery.

## Implemented: option B

`RoStore.load` now parses once. `LoadingHandler` gives every value a
provisional id, `(group << 32) | order first seen`, where the group is an IRI
namespace, a literal language or datatype, or the big integers. Blank nodes,
`xsd:int`, booleans and `xsd:integer` values that are their own id are stored
as they are. It writes each triple to a staging Parquet file
(`StagedTriples`) as a kind and an id per position. The temp dictionaries
hold the distinct values in memory (`ProvisionalGroup`). Their `load()`
sorts each group, writes the dictionary, and keeps an array from provisional
to final id. The staged triples are then read once, in order, and stored
through those arrays. Pass 2 and every lookup during loading are gone.

Measured on `taxonomy.rdf` (16.5M triples, 1.4 GB RDF/XML):

| | Two passes (before) | One pass (now) |
| --- | --- | --- |
| Statements per second, storing | 1 370 | 159 000 (parse and stage) |
| Whole load | about 4.5 min, plus about 3.2 h estimated for pass 2 | 2 min 39 s, everything included |
| Store on disk | | 558 MB |

The rate of the single pass drops while hardwood compresses a staging row
group, which the parse thread waits for. Writing the staging file from a
second thread would hide that.

Memory is now the limit: every distinct value of a dictionary group is held
in a hash map until its dictionary is written. That is fine for the taxonomy;
for data with billions of distinct values option C replaces the maps, with
the staging file and the remap kept as they are.

## Recommendation (before the implementation)

1. Do **B** now. It removes the lookups, needs one parse, and is a contained
   change. Write the staging file so that its layout also suits C:
   triple-ordered Parquet with one column per role.
2. Replace B's in-memory map with **C** when data stops fitting in memory.
   The staging file and the remap step stay. Only the way final ids are
   found changes.
3. Separately, query time has the same fixed ~1 ms per id-to-string read,
   which every result value pays. Options: a cache of recently read rows;
   reading a whole row group per access and keeping it, since the row group of
   an id is `id / 4096`; or, for IRIs and strings, a memory-mapped offsets file
   beside the Parquet file for O(1) access.

## Full taxonomy

`RoStoreTest#taxonomyTripleLoad` loads both taxonomy files, 55 872 533
triples, and counts them back through the store in 280 s.
