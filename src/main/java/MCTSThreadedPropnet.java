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


public class MCTSThreadedPropnet extends StateMachineGamer {

	// Number of depth charges per turn
	private int num_depth_charges;

	// Maximum depth - forces exploration
	private int depth_max;

	// Time buffer
	private int timeBuffer;

	// Count for number of cpu cycles to run depth threads
	private int pd_count;

	// Number of CPUs available for threading
	private int num_cpus;
	private double goal_heuristic_weight;
	private double mobility_heuristic_weight;
	private double opp_mob_heuristic_weight;
	private PropNetImplementation propnetStateMachine;
	private List<PropNetImplementation> threadNets;
	private int charge_depth;

	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		// Initializes instance variables
		timeBuffer = 1000;
		cweight = 15;
		depth_max = 100;
		charge_depth = 0;
		num_depth_charges = 0;
		pd_count = 11;
		num_cpus = Runtime.getRuntime().availableProcessors(); // Update this based on number of threads you can run

		// Create thread propnets
		threadNets = new ArrayList<PropNetImplementation>();
		List<Thread> runthreads = new ArrayList<Thread>();
		List<PropNetThreadGen> ptthreads = new ArrayList<PropNetThreadGen>();

		for (int i = 0; i < num_cpus; i++) {
			PropNetThreadGen pt = new PropNetThreadGen(getMatch().getGame().getRules(), timeout);
			Thread t = new Thread( pt );
			t.start();
			runthreads.add(t);
			ptthreads.add(pt);
			//dct.run();
		}

		for (int i=0; i<num_cpus; i++) {
			try {
				runthreads.get(i).join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		for (int i=0; i<num_cpus; i++) {
			threadNets.add(ptthreads.get(i).getNet());
		}

		// Create a propnet
		propnetStateMachine = threadNets.get(0);
		System.out.println("PROPNET SIZE: " + propnetStateMachine.getNumComponents());

		MachineState initState = propnetStateMachine.getInitialState();
		//propnetStateMachine.renderToFile("ASD");

		//System.out.println(initStateProp);

		// Find cweight
		//StateMachine stateMachine = getStateMachine();
		//MachineState initState = stateMachine.getInitialState();
		//System.out.println(initState);
		double turn_steps = 0;
		double step_count = 0;
		double rewards = 0;
		double reward_count = 0;
		long total_game_time = 0;

		while ( !checkTimeout(timeout) ) {
			List<Thread> runthreads2 = new ArrayList<Thread>();
			List<InitChargeThread> dcthreads = new ArrayList<InitChargeThread>();

			// Only start as many threads as there are cpus
			for (int j = 0; j < num_cpus; j++) {
				InitChargeThread dct = new InitChargeThread(getRole(), threadNets.get(j), initState, timeout, timeBuffer);
				Thread t = new Thread( dct );
				t.start();
				runthreads2.add(t);
				dcthreads.add(dct);
			}

			// Wait for all threads to finish
			for (int j=0; j<num_cpus; j++) {
				try {
					runthreads2.get(j).join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			// Add depth charge score to total and count
			for (int j=0; j<num_cpus; j++) {
				if (dcthreads.get(j).complete()) {
					rewards = rewards + dcthreads.get(j).getValue();
					reward_count++;
					step_count = step_count + dcthreads.get(j).getStepCount();
					turn_steps = turn_steps + dcthreads.get(j).getMoveCount();
					total_game_time = total_game_time + dcthreads.get(j).getGameTime();
				}
			}
		}
		cweight = 0.8*(rewards / reward_count) / Math.sqrt(Math.log(turn_steps / step_count));
		System.out.println(cweight);
		System.out.println(rewards);
		System.out.println(reward_count);
		System.out.println(turn_steps);
		System.out.println(step_count);
		System.out.println(total_game_time / reward_count);

		// Calculate approximate charge depth based on how long it takes to play a game and how many moves there are per turn

		while( charge_depth < 3 ) {
			pd_count--;
			charge_depth = (int) ((3000 * step_count * step_count * reward_count) / (total_game_time * pd_count * turn_steps));
		}
		System.out.println(pd_count);
		System.out.println(charge_depth);
	}

	private MCTSNode select(MCTSNode node, int depth) {
		depth++;
		if ( depth > depth_max ) {
			System.out.println("MAX DEPTH");
			return null;
		}
		if (node.getNumVisits() == 0 || node.getNumChildren() == 0) {
			return node;
		}
		//for (int i = 0; i < node.getNumChildren(); i++) {
		//	MCTSNode child = node.getChild(i);
		//	if (child.getNumVisits() == 0) {
		//		return child;
		//	}
		//}
		MCTSNode result = node;
		int[] scores = new int[node.getNumChildren()];
		int[] indexes = new int[node.getNumChildren()];
		for (int i = 0; i < node.getNumChildren(); i++) {
			MCTSNode child = node.getChild(i);
			int newScore = 0;
			// Minnodes have no action taken
			if ( node.getAction() == null && propnetStateMachine.getRoles().size() > 1 ) {
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
		//StateMachine stateMachine = getStateMachine();

		MachineState state = node.getState();
		Role role = node.getRole();
		int new_children = 0;
		try {
			//if (stateMachine.isTerminal(state)) {
			if (propnetStateMachine.isTerminal(state)) {
				return 0;
			}
			List<Move> ourAvailableActions = propnetStateMachine.getLegalMoves(state, role);
			for (int i = 0; i < ourAvailableActions.size(); i++) {
				Move ourTakenAction = ourAvailableActions.get(i);

				if (isSinglePlayer) {
					List<Move> ourTakenActions = new ArrayList<Move>();
					ourTakenActions.add(ourTakenAction);
					MachineState newState = propnetStateMachine.findNext(ourTakenActions, state);
					MCTSNode grandchild = new MCTSNode(node, role, newState, ourTakenAction);
					node.addChild(grandchild);
					new_children++;
				} else {
					MCTSNode child = new MCTSNode(node, role, state, ourTakenAction);
					node.addChild(child);
					List<List<Move>> theirAvailableActions = propnetStateMachine.getLegalJointMoves(state, role, ourTakenAction);
					for (int j = 0; j < theirAvailableActions.size(); j++) {
						List<Move> theirTakenActions = theirAvailableActions.get(j);
						MachineState newState = propnetStateMachine.findNext(theirTakenActions, state);
						MCTSNode grandchild = new MCTSNode(child, role, newState, null);
						child.addChild(grandchild);
						new_children++;
					}
				}
			}
			return new_children;
		}
		catch(Exception e) {
			throw new GoalDefinitionException(state, role);
		}
	}

	private int simulate(MCTSNode node, long timeout) throws GoalDefinitionException {
		if (propnetStateMachine.isTerminal(node.getState())) {
			return propnetStateMachine.findReward(getRole(), node.getState());
		}
		num_depth_charges = num_depth_charges + pd_count*num_cpus;
		return (int) monteCarlo(node.getRole(), node.getState(), timeout);
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
		//StateMachine stateMachine = getStateMachine();
		//List<Role> roles = stateMachine.getRoles();
		List<Role> roles = propnetStateMachine.getRoles();
		boolean isSinglePlayer = ( roles.size() == 1 );

		Role role = getRole();
		//List<Move> actions = stateMachine.getLegalMoves(state, role);
		List<Move> actions = propnetStateMachine.getLegalMoves(state, role);

		System.out.println(actions);

		if (actions.size() == 1) {
			return actions.get(0);
		}

		MCTSNode root = new MCTSNode(null, role, state, null);
		int counter = 0;

		while (counter < 2000) {
			if (checkTimeout(timeout)) break;
			MCTSNode selectedNode = select(root, 0);

			if (checkTimeout(timeout)) break;
			int numChildren = expand(selectedNode, isSinglePlayer);
			int score = 0;

			for (int i=0; i<selectedNode.getNumChildren(); i++) {
				MCTSNode newNode = selectedNode.getChild(i);

				if (isSinglePlayer) {

					if (checkTimeout(timeout)) break;
					score = simulate(newNode, timeout);

					if (checkTimeout(timeout)) break;
					backpropagate(newNode, score);
				}
				else {
					int numGC = newNode.getNumChildren();
					for (int j=0; j<numGC; j++) {
						MCTSNode gcNode = newNode.getChild(j);

						if (checkTimeout(timeout)) break;
						score = simulate(gcNode, timeout);

						if (checkTimeout(timeout)) break;
						backpropagate(gcNode, score);
				}
				}
			}

			if (checkTimeout(timeout)) break;

			counter += 1;
			if (counter % 5 == 0) {
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
			int utility = 0;
			if (child.getNumVisits() == 0) {
				utility = child.getUtility();
			}
			else {
				utility = child.getUtility() / child.getNumVisits();
			}
			//int utility = selectfn(child);
			System.out.println("child " + i + " " + child.getAction() + " " + utility + " " + child.getNumVisits());
			if (utility > maxUtility) {
				maxUtility = utility;
				bestAction = child.getAction();
			}

			//int newScore = selectfn(child);
		}

		long stop = System.currentTimeMillis();
		System.out.println(num_depth_charges);

		notifyObservers(new GamerSelectedMoveEvent(actions, bestAction, stop - start));
		return bestAction;
	}

	private boolean checkTimeout(long timeout) {
		return (timeout - System.currentTimeMillis() < timeBuffer);
	}

	private double cweight;

	private double monteCarlo(Role role, MachineState state, long timeout) throws GoalDefinitionException {
		try {
			//StateMachine stateMachine = getStateMachine();

			double total = 0;
			int runningCount = 0;

			for (int i=0; i<pd_count; i++) {
				List<Thread> runthreads = new ArrayList<Thread>();
				List<DepthChargeThread> dcthreads = new ArrayList<DepthChargeThread>();

				// Only start as many threads as there are cpus
				for (int j = 0; j < num_cpus; j++) {
					DepthChargeThread dct = new DepthChargeThread(role, threadNets.get(j), state, timeout, timeBuffer, charge_depth);
					Thread t = new Thread( dct );
					t.start();
					runthreads.add(t);
					dcthreads.add(dct);
				}

				// Wait for all threads to finish
				for (int j=0; j<num_cpus; j++) {
					runthreads.get(j).join();
				}

				// Add depth charge score to total and count
				for (int j=0; j<num_cpus; j++) {
					total = total + dcthreads.get(j).getValue();
					runningCount++;
				}

				if (checkTimeout(timeout)) {
					return 0;
				}

			}
			return total / runningCount;
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
		return "MCTS Theaded Propnet Player";
	}

}
