package sib.swiss.swissprot.sparql.ro;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver;
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolverImpl;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.helpers.AbstractSail;

public class RoStore extends AbstractSail {
	private RoValueFactory vf;
	private FederatedServiceResolver federatedServiceResolver = new FederatedServiceResolverImpl();
	private RoNamespaces namespaces;

	@Override
	public boolean isWritable() throws SailException {
		return false;
	}

	@Override
	public RoValueFactory getValueFactory() {
		return vf;
	}

	@Override
	protected void shutDownInternal() throws SailException {

	}

	@Override
	protected SailConnection getConnectionInternal() throws SailException {
		return new RoConnection(this);
	}

	@Override
	protected void initializeInternal() throws SailException {
		final File dataDir = getDataDir();
		if (!dataDir.exists()) {
			if (!dataDir.mkdirs()) {
				throw new SailException("DataDirectory:" + dataDir
						+ " does not exist and can not be created");
			}
		}
		Map<RoDirectories, File> subDataDirs = new EnumMap<>(
				RoDirectories.class);
		for (RoDirectories dir : RoDirectories.values()) {
			File subDataDir = new File(dataDir, dir.getDirectoryName());
			subDataDirs.put(dir, subDataDir);
			if (!subDataDir.exists()) {
				if (!subDataDir.mkdirs()) {
					throw new SailException("SubDataDirectory:" + subDataDir
							+ " does not exist and can not be created");
				}
			}
		}
		namespaces = new RoNamespaces(subDataDirs.get(RoDirectories.NAMESPACES)
				.toPath());

	}

	public FederatedServiceResolver getFederatedServiceResolver() {
		return federatedServiceResolver;
	}

	public RoNamespaces getNamespaces() {
		return namespaces;
	}
}
