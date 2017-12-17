package org.eclipse.jgit.pgm.opt;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

/**
 * Handler which allows to parse option with few values
 *
 * @since 4.2
 */
public class OptionWithValuesListHandler extends OptionHandler<List<?>> {

	/**
	 * Constructor for OptionWithValuesListHandler.
	 *
	 * @param parser
	 *            a {@link org.kohsuke.args4j.CmdLineParser} object.
	 * @param option
	 *            a {@link org.kohsuke.args4j.OptionDef} object.
	 * @param setter
	 *            a {@link org.kohsuke.args4j.spi.Setter} object.
	 */
	public OptionWithValuesListHandler(CmdLineParser parser,
			OptionDef option, Setter<List<?>> setter) {
		super(parser, option, setter);
	}

	/** {@inheritDoc} */
	@Override
	public int parseArguments(Parameters params) throws CmdLineException {
		final List<String> list = new ArrayList<>();
		for (int idx = 0; idx < params.size(); idx++) {
			final String p;
			try {
				p = params.getParameter(idx);
			} catch (CmdLineException cle) {
				break;
			}
			list.add(p);
		}
		setter.addValue(list);
		return list.size();
	}

	/** {@inheritDoc} */
	@Override
	public String getDefaultMetaVariable() {
		return CLIText.get().metaVar_values;
	}

}
