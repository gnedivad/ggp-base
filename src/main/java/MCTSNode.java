import java.util.ArrayList;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;


public class MCTSNode {
	private int numVisits;
	private ArrayList<MCTSNode> children;
	private int utility;
	private MCTSNode parent;
	private Role role;
	private MachineState state;
	private Move action; 	/* Will be null for our player, non-null for their player */

	public MCTSNode(MCTSNode p, Role r, MachineState ms, Move a) {
		parent = p;
		state = ms;
		numVisits = 0;
		utility = 0;
		role = r;
		children = new ArrayList<MCTSNode>();
		action = a;
	}

	public int getNumVisits() {
		return numVisits;
	}

	public void setNumVisits(int n) {
		numVisits = n;
	}

	public int getNumChildren() {
		return children.size();
	}

	public MCTSNode getChild(int i) {
		return children.get(i);
	}

	public ArrayList<MCTSNode> getChildren() {
		return children;
	}

	public void addChild(MCTSNode child) {
		children.add(child);
	}

	public boolean hasParent() {
		return (parent != null);
	}

	public MCTSNode getParent() {
		return parent;
	}

	public Role getRole() {
		return role;
	}

	public MachineState getState() {
		return state;
	}

	public int getUtility() {
		return utility;
	}

	public void setUtility(int u) {
		utility = u;
	}

	public Move getAction() {
		return action;
	}
}
