import java.util.List;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;


public class FixedDepthPlayer extends StateMachineGamer {

	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		// Minimax gamer does no metagaming at the beginning of the match.
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		// We get the current start time
		long start = System.currentTimeMillis();

		/**
		 * We put in memory the list of legal moves from the
		 * current state. The goal of every stateMachineSelectMove()
		 * is to return one of these moves. The choice of which
		 * Move to play is the goal of GGP.
		 */
		Role role = getRole();
		MachineState state = getCurrentState();
		StateMachine stateMachine = getStateMachine();

		int limit = 0;
		long curr_time = System.currentTimeMillis();
		// Iterative deepening.
		List<Move> actions = stateMachine.getLegalMoves(state, role);
		Move action = actions.get(0);
		while (timeout - curr_time > 7000) {
			int score = 0;
			for (int i = 0; i < actions.size(); i++) {
				int result = minScore(role, actions.get(i), state, 0, limit);
				if (result >= score) {
					score = result;
					action = actions.get(i);
				}
			}
			limit++;
			curr_time = System.currentTimeMillis();
		}

		// We get the end time
		// It is mandatory that stop<timeout
		long stop = System.currentTimeMillis();

		notifyObservers(new GamerSelectedMoveEvent(actions, action, stop - start));
		return action;
	}

	private int minScore(Role role, Move action, MachineState state, int level, int limit) {
		StateMachine stateMachine = getStateMachine();
		List<Role> roles = stateMachine.getRoles();

		// Single Player, just go straight to next maxScore
		if ( roles.size() == 1 ) {
			try {
				return maxScore(role, state, level+1, limit);
			}
			catch (Exception e) {
			}
		}

		try {
			// Joint moves given our role makes given action
			List<List<Move>> actions = stateMachine.getLegalJointMoves(state, role, action);
			int score = 100;
			// Number of joint moves
			for (int i = 0; i < actions.size(); i++) {
				List<Move> moves = actions.get(i);
				MachineState newState = stateMachine.findNext(moves, state);
				int result = maxScore(role, newState,level+1,limit);
				if (result < score) {
					score = result;
				}
			}

			return score;
		}
		catch (Exception e) {

		}

		return 0;
	}

	private int maxScore(Role role, MachineState state, int level, int limit) throws GoalDefinitionException {
		try {
			StateMachine stateMachine = getStateMachine();

			if (stateMachine.findTerminalp(state)) {
				return stateMachine.findReward(role, state);
			}
			if ( level >= limit ) {
				return 0;
			}
			List<Move> actions = stateMachine.getLegalMoves(state, role);
			int score = 0;
			for (int i = 0; i < actions.size(); i++) {
				int result = minScore(role, actions.get(i), state, level, limit);
				if (result == 100) {
					return 100;
				}
				if (result > score) {
					score = result;
				}
			}
			return score;
		}
		catch (Exception e) {
			throw new GoalDefinitionException(state, role);
		}
	}

	@Override
	public void stateMachineStop() {
		// TODO Auto-generated method stub

	}

	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub

	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// TODO Auto-generated method stub

	}

	@Override
	public String getName() {
		return "Fixed Depth Player";
	}

}
