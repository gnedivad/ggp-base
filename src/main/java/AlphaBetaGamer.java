import java.util.ArrayList;
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


public class AlphaBetaGamer extends StateMachineGamer {

	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		// Alpha Beta gamer does no metagaming at the beginning of the match.
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

		List<Move> actions = stateMachine.getLegalMoves(state, role);
		Move action = actions.get(0);
		int score = 0;
		for (int i = 0; i < actions.size(); i++) {
			int result = minScore(role, actions.get(i), state, 0, 100);
			if (result == 100) {
				score = result;
				action = actions.get(i);
				break;
			}
			if (result > score) {
				score = result;
				action = actions.get(i);
			}
		}

		// We get the end time
		// It is mandatory that stop<timeout
		long stop = System.currentTimeMillis();

		notifyObservers(new GamerSelectedMoveEvent(actions, action, stop - start));
		return action;
	}

	private int minScore(Role role, Move action, MachineState state, int alpha, int beta) {
		StateMachine stateMachine = getStateMachine();
		List<Role> roles = stateMachine.getRoles();

		// Only works for one opponent
		Role opponent = roles.get(1);

		try {
			List<Move> actions = stateMachine.getLegalMoves(state, opponent);
			for (int i = 0; i < actions.size(); i++) {
				List<Move> moves = new ArrayList<Move>();
				if (role == roles.get(0)) {
					moves.add(action);
					moves.add(actions.get(i));
				} else {
					moves.add(actions.get(i));
					moves.add(action);
				}
				MachineState newState = stateMachine.findNext(moves, state);
				int result = maxScore(role, newState, alpha, beta);
				beta = Math.min(beta, result);
				if (beta <= alpha) {
					return alpha;
				}
			}

			return beta;
		}
		catch (Exception e) {

		}

		return 0;
	}

	private int maxScore(Role role, MachineState state, int alpha, int beta) throws GoalDefinitionException {
		try {
			StateMachine stateMachine = getStateMachine();

			if (stateMachine.findTerminalp(state)) {
				return stateMachine.findReward(role, state);
			}
			List<Move> actions = stateMachine.getLegalMoves(state, role);
			for (int i = 0; i < actions.size(); i++) {
				int result = minScore(role, actions.get(i), state, alpha, beta);
				alpha = Math.max(alpha, result);
				if (alpha >= beta) {
					return beta;
				}
			}
			return alpha;
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
		return "Alpha Beta Gamer";
	}

}
