import java.util.List;

import org.ggp.base.util.gdl.grammar.Gdl;


public class PropNetThreadGen implements Runnable {

	private List<Gdl> gameDes;
	private PropNetImplementation outNet;
	private long thisTime;

	public PropNetThreadGen(List<Gdl> description, long timeout) {
		gameDes = description;
		thisTime = timeout;
		outNet = null;
	}

	@Override
	public void run() {
		try {
			outNet = new PropNetImplementation();
			outNet.initialize(gameDes, thisTime);
		}
		catch (Exception e) {
		}
	}

	public PropNetImplementation getNet() {
		return outNet;
	}
}