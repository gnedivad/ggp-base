import java.util.List;
import java.util.Random;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;


// Like depth charge thread, but this one also gets a bunch of stats that are useful in metagaming
public class InitChargeThread implements Runnable {

	public InitChargeThread(Role role, PropNetImplementation gameMachine, MachineState currState, long timeout, long timeBuffer) {
		thisRole = role;
		thisMachine = gameMachine; //new PropNetImplementation((PropNetImplementation) gameMachine);
		gameMachine.setBaseProps(currState);
		value = 0;
		cumVal = 0;
		thisTimeout = timeout;
		thisBuffer = timeBuffer;
		stepCount = 0;
		moveCount = 0;
		gameTime = 0;
		finish = false;
	}

	@Override
	public void run() {
		try {
			long startTime = System.currentTimeMillis();
			while (!thisMachine.isTerminal()) {
				stepCount++;
				List<List<Move>> jms = thisMachine.getLegalJointMoves();
				moveCount = moveCount + jms.size();

				List<Move> simulatedMoves = jms.get(new Random().nextInt(jms.size()));

				//List<Move> simulatedMoves = thisMachine.getRandomJointMove();
				thisMachine.toNextState(simulatedMoves);
				cumVal = cumVal + thisMachine.getGoal(thisRole);
				if ( checkTimeout() ) {
					break;
				}
			}

			if ( thisMachine.isTerminal()) {
				value = thisMachine.getGoal(thisRole);
				finish = true;
			}
			gameTime = System.currentTimeMillis() - startTime;
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

	public double getCumVal() {
		return cumVal;
	}

	public int getStepCount() {
		return stepCount;
	}

	public int getMoveCount() {
		return moveCount;
	}

	public long getGameTime() {
		return gameTime;
	}

	public boolean complete() {
		return finish;
	}

	private Role thisRole;
	private PropNetImplementation thisMachine;
	private double value;
	private long thisTimeout;
	private long thisBuffer;
	private int stepCount;
	private int moveCount;
	private long gameTime;
	private boolean finish;
	private int cumVal;
}