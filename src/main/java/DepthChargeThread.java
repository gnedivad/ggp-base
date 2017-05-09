import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;


public class DepthChargeThread implements Runnable {

	public DepthChargeThread(Role role, StateMachine gameMachine, MachineState currState, long timeout, long timeBuffer) {
		thisRole = role;
		thisMachine = gameMachine;
		thisState = currState;
		value = -10;
		thisTimeout = timeout;
		thisBuffer = timeBuffer;

	}

	@Override
	public void run() {
		try {
			MachineState state = thisState;
			while (!thisMachine.findTerminalp(state)) {
				List<Move> simulatedMoves = thisMachine.getRandomJointMove(state);
				state = thisMachine.getNextState(state, simulatedMoves);
				if ( checkTimeout() ) {
					break;
				}
			}
			if ( thisMachine.findTerminalp(state)) {
				value = thisMachine.findReward(thisRole, state);
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
	private StateMachine thisMachine;
	private MachineState thisState;
	private double value;
	private long thisTimeout;
	private long thisBuffer;
}