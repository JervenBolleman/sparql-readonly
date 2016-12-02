package sib.swiss.swissprot.sparql.ro;

import java.io.File;

import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.config.SailConfigException;
import org.eclipse.rdf4j.sail.config.SailFactory;
import org.eclipse.rdf4j.sail.config.SailImplConfig;

public class RoStoreFactory implements SailFactory {

	/**
	 * The type of repositories that are created by this factory.
	 * 
	 * @see SailFactory#getSailType()
	 */
	public static final String SAIL_TYPE = "isbsib:BEDFileStore";

	/**
	 * Returns the Sail's type: <tt>openrdf:MemoryStore</tt>.
	 */
	@Override
	public String getSailType() {
		return SAIL_TYPE;
	}

	@Override
	public SailImplConfig getConfig() {
		return new RoConfig();
	}

	@Override
	public Sail getSail(SailImplConfig config) throws SailConfigException {
		if (!SAIL_TYPE.equals(config.getType())) {
			throw new SailConfigException("Invalid Sail type: "
					+ config.getType());
		}

		RoStore memoryStore = new RoStore();

		if (config instanceof RoConfig) {
			RoConfig memConfig = (RoConfig) config;

			memoryStore.setDataDir(new File(memConfig.getFile()));
		}

		return memoryStore;
	}

}
