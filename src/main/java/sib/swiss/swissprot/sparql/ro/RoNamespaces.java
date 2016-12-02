package sib.swiss.swissprot.sparql.ro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.rdf4j.sail.SailException;

public class RoNamespaces implements Map<String, RoNamespace> {

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
	public void forEach(BiConsumer<? super String, ? super RoNamespace> action) {
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
	public RoNamespace computeIfPresent(
			String key,
			BiFunction<? super String, ? super RoNamespace, ? extends RoNamespace> remappingFunction) {
		return namespaces.computeIfPresent(key, remappingFunction);
	}

	@Override
	public RoNamespace compute(
			String key,
			BiFunction<? super String, ? super RoNamespace, ? extends RoNamespace> remappingFunction) {
		return namespaces.compute(key, remappingFunction);
	}

	@Override
	public RoNamespace merge(
			String key,
			RoNamespace value,
			BiFunction<? super RoNamespace, ? super RoNamespace, ? extends RoNamespace> remappingFunction) {
		return namespaces.merge(key, value, remappingFunction);
	}

	private Map<String, RoNamespace> namespaces;

	RoNamespaces(Path path) {
		try (Stream<String> lines = Files.lines(path)) {
			namespaces = new HashMap<>();
			lines.map(RoNamespace::fromSavedString).forEach(
					n -> namespaces.put(n.getPrefix(), n));
		} catch (IOException e) {
			throw new SailException(e);
		}
	}

	public Collection<RoNamespace> getNamespaces() {

		return namespaces.values();
	}

	public RoNamespace getFromId(long id) {

		return null;
	}
}
