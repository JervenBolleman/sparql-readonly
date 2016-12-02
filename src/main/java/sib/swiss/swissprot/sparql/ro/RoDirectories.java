package sib.swiss.swissprot.sparql.ro;

enum RoDirectories {
	PREDICATE_LISTS("predicateLists"), IRI_DICTIONARIES("iriDictionaries"), BNODE_DICTIONARIES(
			"bnodeDictionaries"), NUMBERIC_VALUE_DICTIONARIES(
			"numberDictionaries"), STRING_DICTIONARY("stringDictionaries"), LANG_STRING_DICTIONARIES(
			"langStringDictionaries"), OTHER_VALUE_DICTIONARIES(
			"otherValueDictionaries"), NAMESPACES("namespaces");

	private final String directoryName;

	private RoDirectories(String directoryName) {
		this.directoryName = directoryName;
	}

	public String getDirectoryName() {
		return directoryName;
	}

}
