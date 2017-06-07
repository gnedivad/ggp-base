import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;


public class DepthChargeThread implements Runnable {

	public DepthChargeThread(Role role, PropNetImplementation gameMachine, MachineState currState, long timeout, long timeBuffer, int maxDepth, double goalWeight, double moveWeight) {
		thisRole = role;
		thisMachine = gameMachine; //new PropNetImplementation((PropNetImplementation) gameMachine);
		gameMachine.setBaseProps(currState);
		value = 0;
		thisTimeout = timeout;
		thisBuffer = timeBuffer;
		depth = maxDepth;
		gW = goalWeight;
		mW = moveWeight;

	}

	@Override
	public void run() {
		try {
			int turn_count = 0;
			while (!thisMachine.isTerminal()) {
				turn_count++;
				List<Move> simulatedMoves = thisMachine.getRandomJointMove();
				thisMachine.toNextState(simulatedMoves);
				if ( checkTimeout() || ( turn_count > depth ) ) {
					break;
				}
			}

			if (thisMachine.isTerminal()) {
				value = thisMachine.getGoal(thisRole);
			}
			else { // Heuristic
				value = ( thisMachine.getGoal(thisRole)*gW ) + ( thisMachine.getLegalMoves(thisRole).size()*mW );
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
	private int depth;
	private double gW;
	private double mW;
}