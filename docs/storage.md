# Storage

What a sparql-readonly store holds on disk, how the files refer to each
other, and how reading a statement pattern uses them. This describes what is
implemented; [loading.md](loading.md) describes how the files are written.

The numbers are from a store of `taxonomy.rdf` (16.5M triples, 21
predicates): 100 MiB on disk in 67 files.

## Layout

All data files are Parquet, written with [hardwood](https://hardwood.dev),
compressed with zstd. The other files are small binary or text files.

```mermaid
flowchart LR
    store["#lt;store#gt;/"]
    store --> ns["namespaces/"]
    store --> iri["iriDictionaries/"]
    store --> other["otherValueDictionaries/"]
    store --> num["numberDictionaries/"]
    store --> lists["predicateLists/"]
    store --> wide["wideTables/"]
    ns --> nsfile["iri-namespaces<br/><i>Parquet, one row per IRI namespace</i>"]
    ns --> prefixes["prefixes<br/><i>text, prefixes declared in the data</i>"]
    iri --> iris["iris<br/><i>Parquet, the local names of<br/>all namespaces of names</i>"]
    other --> literals["literals<br/><i>Parquet, all literals</i>"]
    num --> integers["integer_dict<br/><i>Parquet, big integers</i>"]
    lists --> pred["#lt;URL-encoded predicate IRI#gt;/<br/><i>one per predicate, see below</i>"]
    wide --> table["table-#lt;n#gt;<br/><i>Parquet, triple lists merged<br/>on their subjects, see below</i>"]
```

A directory or file that would be empty is not there: a store without big
integers has no `numberDictionaries/`, one without literals no
`otherValueDictionaries/`.

Inside each predicate directory, per non-empty triple list `<list>`:

```mermaid
flowchart LR
    pred["#lt;URL-encoded predicate IRI#gt;/"]
    pred --> predfile["predicate<br/><i>8 bytes: the predicate's IRI id</i>"]
    pred --> list["#lt;list#gt;<br/><i>Parquet: triples sorted by subject</i>"]
    pred --> byobj["#lt;list#gt;-by-object<br/><i>Parquet: the same triples, sorted by object</i>"]
    pred --> graphs["#lt;list#gt;-graphs<br/><i>rows of #lt;list#gt; per named graph,<br/>only if it has triples in one</i>"]
    pred --> widefile["#lt;list#gt;-wide<br/><i>text: the wide table and column<br/>that replace #lt;list#gt;</i>"]
```

### Triple lists

Each predicate has a directory with up to 13 triple lists, one per kind of
subject and kind of object. Only lists with triples have files.

| Subject \ object | IRI | blank node | dictionary literal | `xsd:int` | boolean | `xsd:integer` |
| --- | --- | --- | --- | --- | --- | --- |
| **IRI** | `iri_iri` | `iri_bnode` | `iri_literal` | `iri_integer` | `iri_boolean` | `iri_big_integer` |
| **blank node** | `bnode_iri` | `bnode_bnode` | `bnode_literal`, `bnode_string` | `bnode_integer` | `bnode_boolean` | `bnode_big_integer` |

A list file has two columns, `subject` and `object`, both `INT64` ids (see
"Ids"). It is sorted by subject, then object, and holds every triple once.
Its `-by-object` copy holds the same triples sorted by object, then subject.
Row groups hold 64k triples, so that statistics on the sorted column let a
lookup skip all but the row group that holds its value.

A `-graphs` file holds the number of named graphs, then per graph whether
it is an IRI, its id and a serialized `RoaringBitmap` of the rows of the list
in that graph.

### Wide tables

Lists with one object per subject, and no named graphs, whose subjects
largely overlap are merged into one wide table: a `subject` column and one
`INT64` column of objects per list, `p0`, `p1`, …, null where a subject has
no triple in that list. The subjects, otherwise stored in each of these
lists, are stored once. A merged list keeps its `-by-object` copy; its file
sorted by subject is replaced by `<list>-wide`, a line with the table's path
relative to the predicate directory, a tab, and the column.

Which lists are merged is set by `RoStore.setWideTableOverlap(percent)`, or
`-Dsparql.ro.wideTableOverlap` for `Loader`, default 90. Lists are taken
from the most triples to the fewest; each joins the table, of lists with
the same kind of subject, whose subjects it overlaps most, if
|A ∩ B| / |A ∪ B| is at least the setting, or starts a table of its own. A
table needs two lists. At 0 every such list with IRI subjects ends up in one
table, and those with blank node subjects in another; above 100 nothing is
merged.

On the taxonomy store:

| Overlap | Tables | Lists merged | Store |
| --- | --- | --- | --- |
| none (101) | 0 | 0 | 100 MiB, 67 files |
| 90 | 2: `type` + `partOfLineage`, `scientificName` + `subClassOf` | 4 | 95 MiB, 69 files |
| 0 | 1 | 11 | 90 MiB, 68 files |

### Dictionaries

| File | Columns | Row groups | Sorted by |
| --- | --- | --- | --- |
| `namespaces/iri-namespaces` | `id` INT64, `namespace` string; for a prefix-and-number namespace also `number_prefix` string, `name_length` INT32, `numbers` bytes | default | id |
| `iriDictionaries/iris` | `namespace` INT64, `id_col` INT64, `iri_value` string | 4096 rows | namespace, then local name |
| `otherValueDictionaries/literals` | `id_col` INT64, `label` string, `datatype` string, `language` optional string | 4096 rows | label, within each language or datatype |
| `numberDictionaries/integer_dict` | `id_col` INT64, `int_long_value` string | 4096 rows | numeric value |

In `iris` the `id_col` is the row of the name within its namespace; in the
other dictionaries it is the row in the file. Small row groups let a lookup
by id or by value decode only 4096 rows; in `iris`, statistics on the
namespace column first narrow a lookup to its namespace.

A namespace whose local names are all one prefix followed by a number of one
length (`GO_0005634`) has no names in `iris`: its row in `iri-namespaces`
holds the prefix, the length and a serialized `RoaringBitmap` of the numbers
present.

`RoStore.init()` on a directory that holds a store opens all of these, so a
store can be loaded by one process and read by another.

## Ids

Every value in a triple list is a 64-bit id. Which dictionary, if any, gives
its value follows from the list it is in.

| Kind | Id |
| --- | --- |
| IRI | `namespace (32 bits) \| local id (32 bits)`: the local id is the row of the name within its namespace in `iris`, or the number after the prefix. Namespaces are numbered largest first. |
| blank node | a number given while loading |
| dictionary literal | the row in `literals` |
| `xsd:int` | the value |
| boolean | 1 or 0 |
| `xsd:integer` | the value if it is a long without the high bit; otherwise the row in `integer_dict` with the high bit set |

```mermaid
flowchart LR
    row["triple list row<br/>subject id | object id"]
    row -->|IRI| split["high 32 bits: namespace<br/>low 32 bits: local id"]
    split --> nsfile["iris row of the namespace<br/>or prefix + number"]
    row -->|literal| lit["literals row"]
    row -->|"xsd:integer,<br/>high bit set"| big["integer_dict row"]
    row -->|"xsd:int, boolean,<br/>small xsd:integer,<br/>blank node"| self["the id is the value"]
```

## Reading a statement pattern

```mermaid
flowchart TD
    pattern["?s p ?o with what is bound<br/>and FILTERs on ?o"] --> resolve["resolve bound values once<br/>to kind and id (Term)"]
    resolve -->|"not in the store"| empty["no statements"]
    resolve --> select["predicate directories:<br/>the one of p, or all"]
    select --> kinds["lists whose subject and object kind<br/>match the bound values,<br/>and that a pushed FILTER can match"]
    kinds --> which{"what is bound?"}
    which -->|"the subject"| so["#lt;list#gt;<br/>filter on subject (and object)"]
    which -->|"only the object,<br/>or a FILTER on it"| os["#lt;list#gt;-by-object<br/>filter on object"]
    which -->|nothing| scan["#lt;list#gt;, all rows"]
    so --> rows["rows of ids → Ro values,<br/>decoded from the dictionaries<br/>when they are used"]
    os --> rows
    scan --> rows
```

Bound values that came from this store, as in a join, carry their id;
others are looked up in the dictionaries once per pattern. A FILTER that
compares the object with a number, or with a boolean, is pushed down as a
range or set on the object column of the lists where values are stored as
themselves, and rules out lists that can not match; the FILTER itself is
still applied to what is read.

On the taxonomy store: one subject and predicate, 6 ms; one object and
predicate (3 statements), 5 ms; all statements of one subject, 68 ms.

### Stars of patterns

Patterns that are joined and share an unbound subject variable, such as
`?s up:scientificName ?name ; up:rank ?rank ; rdfs:subClassOf ?parent`,
are evaluated together (`StarJoinOptimizer`, `StarJoinStep`):

- with the subject bound by an enclosing join, each pattern is a lookup of
  that subject;
- if a pattern with a bound object has at most 1000 triples, its subjects
  are looked up in the other patterns;
- otherwise the lists of all patterns, sorted by subject, are read once and
  merged, with bound objects as filters on those lists: a subject every
  pattern has gives the combinations of its objects. Patterns whose list
  is a column of the same wide table are read together, as one pass over
  that table with a filter that their columns are not null (or equal to
  the bound object): each row is already a subject with all their objects.

On the taxonomy store all 1.9 million species with their name and parent
take 4 s; one lookup per pattern per species, as a nested loop join does,
takes about 8.5 ms per row, hours in all.

Counting the 2.1 million taxa with a name, rank, parent and lineage flag:

| Overlap | Time |
| --- | --- |
| none | 3.9 s |
| 90 (`rank` not merged) | 3.3 s |
| 0 (all four in one table) | 2.5 s |

Most of what is left is building RDF4J binding sets, not reading the
store.

## What the store consists of

```mermaid
pie showData
    title taxonomy.rdf store, MB on disk
    "triples by subject" : 35
    "triples by object" : 34
    "literals" : 29
    "IRI local names" : 5.5
    "IRI namespaces" : 0.4
```

## Duplication, and how it was removed

The first version of this store, at 583 MiB and about 27 000 files, held:

1. **A copy of the namespace table next to every triple list,** also lists
   without triples: 273 identical files of 1.46 MB, two thirds of the store,
   that nothing read, and 26 of the 40 seconds of writing the triple lists.
   The table is now written once, as `namespaces/iri-namespaces`, and read
   by `RoStore.init()`.
2. **Two context files per list, also without named graphs:** 546 files. A
   list now has one `-graphs` file, only if it has triples in a named graph.
3. **One dictionary file per namespace:** 25 912 files holding 11.7 MB in
   111 MB of filesystem blocks. All local names are now in one file, `iris`,
   and the prefix-and-number namespaces in the rows of `iri-namespaces`.
4. **Directories that were never written** are no longer created, and a
   load removes the directories it left empty.

Together this brought the store to 100 MiB in 67 files, and loading from
137 to 110 s. Lookups that resolve an IRI became about 2 ms slower, as the
IRI file is now shared by all namespaces; large namespaces could get a file
of their own again if that matters.

What is still duplicated:

- **The copy sorted by object,** 34 MB, every triple twice. It is what makes
  lookups by object fast, so it is worth its size for large lists. For lists
  of one row group, 64k triples or less, it gains nothing: 12 of the 21
  taxonomy lists, though only 1.2 MB of the 34. These could skip it, as
  could lists with a single distinct object.
- **The same subjects in the lists of several predicates,** for lists with
  one object per subject, is removed by the wide tables, as far as their
  overlap setting allows. Lists with several objects per subject still
  each store their subjects.
- `bnode_string` lists are never filled: loading puts blank node subjects
  with literal objects in `bnode_literal`.

## Limitations

- Context bitmaps are written but not read: statements come back without
  their context.
