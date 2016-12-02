

The aim of this project is to have a SPARQL capabable triple store for 
datasets that are bulkloaded and then do not change, have a small number of 
predicates (<1024), limited number of graphs (<128) but large numbers of 
triples (20 billion>) In other words datasets that look like UniProt in 2016.


Literals, IRIs and blanknodes are stored in separate dictionaries.
Each hopefully optimized for their contents. IRIs are split into 
multiple dictionaries, the first selecting on a namespace (in this case
last '/' unless overriden) then the rest of the IRI localname. We aim 
to detect if a the localname part is a digit. In which case they 
will be stored in a bitset. As we are readonly the dictionary will
be stored sorted.

During loading we need two passes over the data first to create the value dictionaries, then to build the triple tables. There will be a triple table for each predicate + value type combination. 
