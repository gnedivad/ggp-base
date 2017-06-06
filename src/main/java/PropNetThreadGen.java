import java.util.List;

import org.ggp.base.util.gdl.grammar.Gdl;


public class PropNetThreadGen implements Runnable {

	private List<Gdl> gameDes;
	private PropNetImplementation outNet;

	public PropNetThreadGen(List<Gdl> description) {
		gameDes = description;
		outNet = null;
	}

	@Override
	public void run() {
		try {
			outNet = new PropNetImplementation();
			outNet.initialize(gameDes);
		}
		catch (Exception e) {
		}
	}

	public PropNetImplementation getNet() {
		return outNet;
	}
}