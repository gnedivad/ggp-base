import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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


public class MonteCarloTreeSearchPlayer extends StateMachineGamer {

	private int timeBuffer;
	private int numMoves;

	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		// Initializes instance variables
		numMoves = 0;
		timeBuffer = 3000;
	}

	private MCTSNode select(MCTSNode node) {
		if (node.getNumVisits() == 0) {
			return node;
		}
		for (int i = 0; i < node.getNumChildren(); i++) {
			MCTSNode child = node.getChild(i);
			if (child.getNumVisits() == 0) {
				return child;
			}
		}
		int score = 0;
		MCTSNode result = node;
		for (int i = 0; i < node.getNumChildren(); i++) {
			MCTSNode child = node.getChild(i);
			int newScore = selectfn(child);
			if (newScore > score) {
				score = newScore;
				result = child;
			}
		}
		return select(result);
	}

	private int selectfn(MCTSNode node) {
		double a = (double) node.getUtility() / node.getNumVisits();
		double b = Math.sqrt(2 * Math.log(node.getParent().getNumVisits()) / node.getNumVisits());
		return (int) (a + b);
	}

	/**
	 * I'm using separate min nodes (blue) and max nodes (min), which isn't
	 * the most efficient implementation since most of the information is
	 * duplicated (state is the same for each pair of min and max nodes and
	 * role is the same for all of the nodes in the tree rn lol).
	 *
	 * I can squash them together and make it more space-efficient later, after
	 * I get something functional.
	 */
	private int expand(MCTSNode node, boolean isSinglePlayer) throws GoalDefinitionException {
		StateMachine stateMachine = getStateMachine();

		MachineState state = node.getState();
		Role role = node.getRole();
		try {
			List<Move> ourAvailableActions = stateMachine.getLegalMoves(state, role);
			for (int i = 0; i < ourAvailableActions.size(); i++) {
				Move ourTakenAction = ourAvailableActions.get(i);

				// minNodes (blue) are the exact same as maxNodes (red) except
				// their action is non-null (i.e. role and state are the same)
				MCTSNode child = new MCTSNode(node, role, state, ourTakenAction);
				node.addChild(child);

				if (isSinglePlayer) {
					List<Move> ourTakenActions = new ArrayList<Move>();
					ourTakenActions.add(ourTakenAction);
					MachineState newState = stateMachine.findNext(ourTakenActions, state);
					MCTSNode grandchild = new MCTSNode(child, role, newState, null);
					child.addChild(grandchild);
				} else {
					List<List<Move>> theirAvailableActions = stateMachine.getLegalJointMoves(state, role, ourTakenAction);
					for (int j = 0; j < theirAvailableActions.size(); j++) {
						List<Move> theirTakenActions = theirAvailableActions.get(j);
						MachineState newState = stateMachine.findNext(theirTakenActions, state);
						MCTSNode grandchild = new MCTSNode(child, role, newState, null);
						child.addChild(grandchild);
					}
				}
			}

			/*
			List<Move> ourAvailableActions = stateMachine.getLegalMoves(state, role);
			if (ourAvailableActions.size() > node.getNumChildren()) {
				Move ourTakenAction = ourAvailableActions.get(node.getNumChildren());
				MCTSNode child = new MCTSNode(node, role, state, ourTakenAction);
				node.addChild(child);

				if (isSinglePlayer) {
					List<Move> ourTakenActions = new ArrayList<Move>();
					ourTakenActions.add(ourTakenAction);
					MachineState newState = stateMachine.findNext(ourTakenActions, state);
					MCTSNode grandchild = new MCTSNode(child, role, newState, null);
					child.addChild(grandchild);
				} else {
					List<List<Move>> theirAvailableActions = stateMachine.getLegalJointMoves(state, role, ourTakenAction);
					if (theirAvailableActions.size() > child.getNumChildren()) {
						List<Move> theirTakenActions = theirAvailableActions.get(child.getNumChildren());
						MachineState newState = stateMachine.findNext(theirTakenActions, state);
						MCTSNode grandchild = new MCTSNode(child, role, newState, null);
						child.addChild(grandchild);
					}
				}
			}
			*/
			return ourAvailableActions.size();
		}
		catch(Exception e) {
			throw new GoalDefinitionException(state, role);
		}
	}

	private int simulate(MCTSNode node, long timeout) throws GoalDefinitionException {
		return (int) monteCarlo(node.getRole(), node.getState(), 4, timeout);
	}

	private int depthCharge(Role role, MachineState state, long timeout) throws GoalDefinitionException {
		try {
			StateMachine stateMachine = getStateMachine();

			if (stateMachine.findTerminalp(state)) {
				return stateMachine.findReward(role, state);
			}
			if (checkTimeout(timeout)) {
				return 0;
			}

			List<Move> simulatedMoves = new ArrayList<Move>();
			List<Role> roles = stateMachine.getRoles();
			Random rand = new Random();
			for (int i = 0; i < roles.size(); i++) {
				List<Move> options = stateMachine.getLegalMoves(state, roles.get(i));
				simulatedMoves.add(options.get(rand.nextInt(options.size())));
			}
			MachineState newState = stateMachine.getNextState(state, simulatedMoves);
			return depthCharge(role, newState, timeout);
		}
		catch (Exception e) {
			throw new GoalDefinitionException(state, role);
		}
	}

	private void backpropagate(MCTSNode node, int score) {
		node.setNumVisits(node.getNumVisits() + 1);
		node.setUtility(node.getUtility() + score);
		if (node.hasParent()) {
			backpropagate(node.getParent(), score);
		}
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		long start = System.currentTimeMillis();

		MachineState state = getCurrentState();
		StateMachine stateMachine = getStateMachine();
		List<Role> roles = stateMachine.getRoles();
		boolean isSinglePlayer = roles.size() == 1;

		Role role = getRole();
		List<Move> actions = stateMachine.getLegalMoves(state, role);

		if (actions.size() == 1) {
			return actions.get(0);
		}

		MCTSNode root = new MCTSNode(null, role, state, null);
		int counter = 0;

		while (counter < 200) {
			if (checkTimeout(timeout)) break;
			MCTSNode selectedNode = select(root);

			if (checkTimeout(timeout)) break;
			int numChildren = expand(selectedNode, isSinglePlayer);

			if (checkTimeout(timeout)) break;
			int score = simulate(selectedNode, timeout);

			if (checkTimeout(timeout)) break;
			backpropagate(selectedNode, score);

			counter += 1;
			if (counter % 10 == 0) {
				System.out.println("num new children: " + numChildren);
				System.out.println("num visits: " + selectedNode.getNumVisits());
				System.out.println(counter + " " + score + " " + root.getUtility());
			}
			if (numChildren == 1) {
				System.out.println("Why?? " + counter);
			}
		}


		System.out.println();
		System.out.println();
		System.out.println("num_children: " + root.getNumChildren());
		int maxUtility = 0;
		Move bestAction = actions.get(0);

		for (int i = 0; i < root.getNumChildren(); i++) {
			MCTSNode child = root.getChild(i);
			int utility = child.getUtility();
			System.out.println("child " + i + " " + child.getAction() + " " + utility);
			if (utility > maxUtility) {
				maxUtility = utility;
				bestAction = child.getAction();
			}
		}

		long stop = System.currentTimeMillis();

		notifyObservers(new GamerSelectedMoveEvent(actions, bestAction, stop - start));
		return bestAction;
	}

	private boolean checkTimeout(long timeout) {
		return (timeout - System.currentTimeMillis() < timeBuffer);
	}

	private double monteCarlo(Role role, MachineState state, int probeCount, long timeout) throws GoalDefinitionException {
		double total = 0.0;
		int runningCount = 0;
		try {

			for (int i = 0; i < probeCount; i++) {
				// depthCharge returns 0 after timeout exceeded, so we shouldn't count it to the runningCount
				total = total + depthCharge(role, state, timeout);
				if (checkTimeout(timeout)) {
					break;
				}
				runningCount++;

			}
			return total / runningCount;
		}
		catch (Exception e) {
			System.out.println("Fucking up...");
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
		return "Monte Carlo Tree Search Player";
	}

}
