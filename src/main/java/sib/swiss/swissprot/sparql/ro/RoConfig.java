package sib.swiss.swissprot.sparql.ro;

import static sib.swiss.swissprot.sparql.ro.RoStoreSchema.FILE;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.sail.config.AbstractSailImplConfig;
import org.eclipse.rdf4j.sail.config.SailConfigException;

public class RoConfig extends AbstractSailImplConfig {

	private String file;

	public RoConfig() {
		super(RoStoreFactory.SAIL_TYPE);
	}

	public String getFile() {
		return file;
	}

	@Override
	public void parse(Model graph, Resource implNode)
			throws SailConfigException {
		super.parse(graph, implNode);

		LinkedHashModel model = new LinkedHashModel(graph);

		for (Value space : model.filter(implNode, FILE, null).objects()) {
			try {
				setFile(space.stringValue());
			} catch (IllegalArgumentException e) {
				throw new SailConfigException("String value required for "
						+ FILE + " property, found " + space);
			}
		}

	}

	private void setFile(String stringValue) {
		this.file = stringValue;

	}

	@Override
	public Resource export(Model model) {
		ValueFactory valueFactory = SimpleValueFactory.getInstance();
		Resource self = super.export(model);
		model.add(self, FILE, valueFactory.createLiteral(file));
		return self;
	}
}
