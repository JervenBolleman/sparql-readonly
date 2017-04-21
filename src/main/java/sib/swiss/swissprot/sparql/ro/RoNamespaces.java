package sib.swiss.swissprot.sparql.ro;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class RoNamespaces implements Map<String, RoNamespace> {

	private Map<String, RoNamespace> namespaces;
	private RoNamespace[] namespaceArray;

	@Override
	public int size() {
		return namespaces.size();
	}

	@Override
	public boolean isEmpty() {
		return namespaces.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return namespaces.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return namespaces.containsValue(value);
	}

	@Override
	public RoNamespace get(Object key) {
		return namespaces.get(key);
	}

	@Override
	public RoNamespace put(String key, RoNamespace value) {
		return namespaces.put(key, value);
	}

	@Override
	public RoNamespace remove(Object key) {
		return namespaces.remove(key);
	}

	@Override
	public void putAll(Map<? extends String, ? extends RoNamespace> m) {
		namespaces.putAll(m);
	}

	@Override
	public void clear() {
		namespaces.clear();
	}

	@Override
	public Set<String> keySet() {
		return namespaces.keySet();
	}

	@Override
	public Collection<RoNamespace> values() {
		return namespaces.values();
	}

	@Override
	public Set<java.util.Map.Entry<String, RoNamespace>> entrySet() {
		return namespaces.entrySet();
	}

	@Override
	public boolean equals(Object o) {
		return namespaces.equals(o);
	}

	@Override
	public int hashCode() {
		return namespaces.hashCode();
	}

	@Override
	public RoNamespace getOrDefault(Object key, RoNamespace defaultValue) {
		return namespaces.getOrDefault(key, defaultValue);
	}

	@Override
	public void forEach(
			BiConsumer<? super String, ? super RoNamespace> action) {
		namespaces.forEach(action);
	}

	@Override
	public void replaceAll(
			BiFunction<? super String, ? super RoNamespace, ? extends RoNamespace> function) {
		namespaces.replaceAll(function);
	}

	@Override
	public RoNamespace putIfAbsent(String key, RoNamespace value) {
		return namespaces.putIfAbsent(key, value);
	}

	@Override
	public boolean remove(Object key, Object value) {
		return namespaces.remove(key, value);
	}

	@Override
	public boolean replace(String key, RoNamespace oldValue,
			RoNamespace newValue) {
		return namespaces.replace(key, oldValue, newValue);
	}

	@Override
	public RoNamespace replace(String key, RoNamespace value) {
		return namespaces.replace(key, value);
	}

	@Override
	public RoNamespace computeIfAbsent(String key,
			Function<? super String, ? extends RoNamespace> mappingFunction) {
		return namespaces.computeIfAbsent(key, mappingFunction);
	}

	@Override
	public RoNamespace computeIfPresent(String key,
			BiFunction<? super String, ? super RoNamespace, ? extends RoNamespace> remappingFunction) {
		return namespaces.computeIfPresent(key, remappingFunction);
	}

	@Override
	public RoNamespace compute(String key,
			BiFunction<? super String, ? super RoNamespace, ? extends RoNamespace> remappingFunction) {
		return namespaces.compute(key, remappingFunction);
	}

	@Override
	public RoNamespace merge(String key, RoNamespace value,
			BiFunction<? super RoNamespace, ? super RoNamespace, ? extends RoNamespace> remappingFunction) {
		return namespaces.merge(key, value, remappingFunction);
	}

	public RoNamespaces() {
		namespaceArray = new RoNamespace[0];
		namespaces = new LinkedHashMap<>();
	}

	public Collection<RoNamespace> getNamespaces() {

		return namespaces.values();
	}

	public RoNamespace getFromId(long id) {

		int intId = (int) (id >> 32);
		return namespaceArray[intId];
	}

	static RoNamespace fromSavedString(String s, int i) {
		int firstColon = s.indexOf(':');
		String prefix = s.substring(0, firstColon);
		String namespace = s.substring(firstColon + 1);
		return new RoNamespace(prefix, namespace, i);
	}

	public void add(String prefix, String uri) {
		if (!namespaces.containsValue(uri)) {
			namespaceArray = Arrays.copyOf(namespaceArray,
					namespaceArray.length + 1);
			RoNamespace roNamespace = new RoNamespace(prefix, uri,
					namespaceArray.length - 1);
			namespaceArray[namespaceArray.length - 1] = roNamespace;
			namespaces.put(prefix, roNamespace);
		}
	}

	public static RoNamespaces getInstance() {
		return null;
	}

	public RoNamespace putOrGet(int i, String namespace) {
		if (namespaceArray.length < i)
			return namespaceArray[i];
		else if (i >= namespaceArray.length) {
			namespaceArray = Arrays.copyOf(namespaceArray, i + 1);
			RoNamespace toAdd = new RoNamespace(null, namespace, i);
			namespaceArray[i] = toAdd;
			return toAdd;

		} else
			return getNamespaces().stream()
					.filter(n -> n.getName().equals(namespace)).findAny().get();

	}
}
