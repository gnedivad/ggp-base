import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;


public class DepthChargeThread implements Runnable {

	public DepthChargeThread(Role role, PropNetImplementation gameMachine, MachineState currState, long timeout, long timeBuffer) {
		thisRole = role;
		thisMachine = gameMachine; //new PropNetImplementation((PropNetImplementation) gameMachine);
		gameMachine.setBaseProps(currState);
		value = 0;
		thisTimeout = timeout;
		thisBuffer = timeBuffer;

	}

	@Override
	public void run() {
		try {
			while (!thisMachine.isTerminal()) {
				List<Move> simulatedMoves = thisMachine.getRandomJointMove();
				thisMachine.toNextState(simulatedMoves);
				if ( checkTimeout() ) {
					break;
				}
			}

			if ( thisMachine.isTerminal()) {
				value = thisMachine.getGoal(thisRole);
			}
		}
		catch (Exception e) {
		}
	}

	private Boolean checkTimeout() {
		return (thisTimeout - System.currentTimeMillis() < thisBuffer);
	}

	public double getValue() {
		return value;
	}

	private Role thisRole;
	private PropNetImplementation thisMachine;
	private double value;
	private long thisTimeout;
	private long thisBuffer;
}