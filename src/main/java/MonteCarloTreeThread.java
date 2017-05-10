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


public class MonteCarloTreeThread extends StateMachineGamer {

	private int depth_max;
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
		cweight = 35;
		depth_max = 20;
	}

	private MCTSNode select(MCTSNode node, int depth) {
		depth++;
		if ( depth > depth_max ) {
			System.out.println("MAX DEPTH");
			return null;
		}
		if (node.getNumVisits() == 0) {
			return node;
		}
		for (int i = 0; i < node.getNumChildren(); i++) {
			MCTSNode child = node.getChild(i);
			if (child.getNumVisits() == 0) {
				return child;
			}
		}
		MCTSNode result = node;
		int[] scores = new int[node.getNumChildren()];
		int[] indexes = new int[node.getNumChildren()];
		for (int i = 0; i < node.getNumChildren(); i++) {
			MCTSNode child = node.getChild(i);
			int newScore = 0;
			// Minnodes have no action taken
			if ( node.getAction() == null ) {
				newScore = selectfn(child, 1);
			}
			else {
				newScore = selectfn(child, -1);
			}
			//if (newScore > score) {
			//	score = newScore;
			//	result = child;
			//}
			scores[i] = newScore;
			indexes[i] = i;

		}
		//System.out.println(scores[0]+", "+scores[1]+", "+scores[2]+", "+scores[3]+", "+scores[4]+", ");
		quicksort( scores, indexes );
		//System.out.println(scores[0]+", "+scores[1]+", "+scores[2]+", "+scores[3]+", "+scores[4]+", ");
		//System.out.println(indexes[0]+", "+indexes[1]+", "+indexes[2]+", "+indexes[3]+", "+indexes[4]+", ");

		for ( int i=node.getNumChildren()-1; i>=0; i-- ) {
			result = node.getChild(indexes[i]);
			MCTSNode next = select(result,depth);
			if ( next != null ) {
				return next;
			}
		}
		return null;
	}

	public static void quicksort(int[] main, int[] index) {
	    quicksort(main, index, 0, index.length - 1);
	}

	// quicksort a[left] to a[right]
	public static void quicksort(int[] a, int[] index, int left, int right) {
	    if (right <= left) return;
	    int i = partition(a, index, left, right);
	    quicksort(a, index, left, i-1);
	    quicksort(a, index, i+1, right);
	}

	// partition a[left] to a[right], assumes left < right
	private static int partition(int[] a, int[] index,
	int left, int right) {
	    int i = left - 1;
	    int j = right;
	    while (true) {
	        while (less(a[++i], a[right]))      // find item on left to swap
	            ;                               // a[right] acts as sentinel
	        while (less(a[right], a[--j]))      // find item on right to swap
	            if (j == left) break;           // don't go out-of-bounds
	        if (i >= j) break;                  // check if pointers cross
	        exch(a, index, i, j);               // swap two elements into place
	    }
	    exch(a, index, i, right);               // swap with partition element
	    return i;
	}

	// is x < y ?
	private static boolean less(int x, int y) {
	    return (x < y);
	}

	// exchange a[i] and a[j]
	private static void exch(int[] a, int[] index, int i, int j) {
	    int swap = a[i];
	    a[i] = a[j];
	    a[j] = swap;
	    int b = index[i];
	    index[i] = index[j];
	    index[j] = b;
	}

	private int selectfn(MCTSNode node, double neg) {
		double a = neg*( (double) node.getUtility() ) / ( (double) node.getNumVisits() );
		double b = cweight*Math.sqrt(Math.log(((double) node.getParent().getNumVisits())) / ((double) node.getNumVisits()));
		//System.out.print("A:" );
		//System.out.print(a);
		//System.out.print(", B: " );
		//System.out.println(b);
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
		int new_children = 0;
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
					MCTSNode grandchild = new MCTSNode(child, role, newState, ourTakenAction);
					child.addChild(grandchild);
					new_children++;
				} else {
					List<List<Move>> theirAvailableActions = stateMachine.getLegalJointMoves(state, role, ourTakenAction);
					for (int j = 0; j < theirAvailableActions.size(); j++) {
						List<Move> theirTakenActions = theirAvailableActions.get(j);
						MachineState newState = stateMachine.findNext(theirTakenActions, state);
						MCTSNode grandchild = new MCTSNode(child, role, newState, null);
						child.addChild(grandchild);
						new_children++;
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
			return new_children;//ourAvailableActions.size();
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
			MCTSNode selectedNode = select(root, 0);

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
			int utility = child.getUtility() / child.getNumVisits();
			//int utility = selectfn(child);
			System.out.println("child " + i + " " + child.getAction() + " " + utility + " " + child.getNumVisits());
			if (utility > maxUtility) {
				maxUtility = utility;
				bestAction = child.getAction();
			}

			//int newScore = selectfn(child);
		}

		long stop = System.currentTimeMillis();

		notifyObservers(new GamerSelectedMoveEvent(actions, bestAction, stop - start));
		return bestAction;
	}

	private boolean checkTimeout(long timeout) {
		return (timeout - System.currentTimeMillis() < timeBuffer);
	}

	private double cweight;

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
		return "Monte Carlo Tree Thread Player";
	}

}
