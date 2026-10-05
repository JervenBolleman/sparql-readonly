package sib.swiss.swissprot.sparql.ro;

enum RoDirectories {
	PREDICATE_LISTS("predicateLists"), IRI_DICTIONARIES("iriDictionaries"),
			NUMBERIC_VALUE_DICTIONARIES("numberDictionaries"),
			OTHER_VALUE_DICTIONARIES("otherValueDictionaries"), NAMESPACES("namespaces");

	private final String directoryName;

	private RoDirectories(String directoryName) {
		this.directoryName = directoryName;
	}

	public String getDirectoryName() {
		return directoryName;
	}

}
