package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.propnet.architecture.Component;

/**
 * The Constant class is designed to represent nodes with fixed logical values.
 */
@SuppressWarnings("serial")
public final class Constant extends Component
{
	/** The value of the constant. */
	private final boolean value;
	private final boolean legal;

	/**
	 * Creates a new Constant with value <tt>value</tt>.
	 *
	 * @param value
	 *            The value of the Constant.
	 */
	public Constant(boolean value)
	{
		this.value = value;
		this.legal = value;
	}

	/**
	 * Returns the value that the constant was initialized to.
	 *
	 * @see org.ggp.base.util.propnet.architecture.Component#getValue()
	 */
	@Override
	public boolean getValue()
	{
		return value;
	}

	@Override
	public boolean getLegal()
	{
		return legal;
	}

	/**
	 * @see org.ggp.base.util.propnet.architecture.Component#toString()
	 */
	@Override
	public String toString()
	{
		return toDot("doublecircle", "grey", Boolean.toString(value).toUpperCase());
	}
}