import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;


@SuppressWarnings("unused")
public class PropNetImplementation extends StateMachine {
    /** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Proposition> ordering;
    /** The player roles */
    private List<Role> roles;

    /** List of base proposition booleans **/
    /** List of input proposition booleans **/

    public PropNetImplementation() {
    	this.propNet = null;
    	this.ordering = null;
    	this.roles = null;
    }

    public PropNetImplementation( PropNetImplementation other ) {
    	this.propNet = new PropNet( other.propNet );
    	this.ordering = other.ordering;
    	this.roles = other.roles;
    }

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
        try {
            propNet = OptimizingPropNetFactory.create(description);
            roles = propNet.getRoles();
            ordering = getOrdering();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Renders prop net to file
     */
    public void renderToFile(String filename) {
    	propNet.renderToFile(filename);
    }


    /**
     * Computes if the state is terminal. Should return the value
     * of the terminal proposition for the state.
     */
    @Override
    public boolean isTerminal(MachineState state) {
    	// Set base propositions based on state
    	setBaseProps(state);

    	// propagate
    	propagatePropNet();

    	// Check terminal condition
    	return this.isTerminal();
    }

    /**
     * This version of is terminal checks based on what is currently set in the propnet.
     * Does not require reset.
     */
    public boolean isTerminal() {
    	Proposition termProp = propNet.getTerminalProposition();
    	return termProp.getValue();
    }

    /**
     * Computes the goal for a role in the current state.
     * Should return the value of the goal proposition that
     * is true for that role. If there is not exactly one goal
     * proposition true for that role, then you should throw a
     * GoalDefinitionException because the goal is ill-defined.
     */
    @Override
    public int getGoal(MachineState state, Role role)
            throws GoalDefinitionException {
    	// Set base propositions based on state
    	setBaseProps(state);

    	// Find goal propositions
    	return this.getGoal(role);
    }

    /**
     * Computes the goal for a role in the currently loaded state.
     */
    public int getGoal(Role role) {
    	// propagate
    	propagatePropNet();

    	// Search for a true goal proposition
    	for (Proposition gp : propNet.getGoalPropositions().get(role)) {
    		if (gp.getValue()) {
    			return getGoalValue(gp);
    		}
    	}
    	// Else
    	return 0;
    }

    /**
     * Returns the initial state. The initial state can be computed
     * by only setting the truth value of the INIT proposition to true,
     * and then computing the resulting state.
     */
    @Override
    public MachineState getInitialState() {

    	Set<GdlSentence> contents = new HashSet<GdlSentence>();

    	// Set initial state to true
    	Proposition p = propNet.getInitProposition();
        p.setValue(true);

        // Transition the prop net from the initial proposition
        transitionPropNet();

        // Read base states after initialize transition
        MachineState state = getStateFromBase();

        p.setValue(false);
        return state;
    }

    /**
     * Computes all possible actions for role.
     */
    @Override
    public List<Move> findActions(Role role)
            throws MoveDefinitionException {

    	// Loop through inputs, figure out which ones associated with role
    	List<Move> moves = new ArrayList<Move>();
    	Map<GdlSentence, Proposition> gmapp = propNet.getInputPropositions();
    	for ( GdlSentence g : gmapp.keySet() ) {
			GdlRelation relation = (GdlRelation) g;
			Role thisRole = new Role((GdlConstant) relation.get(0));
    		if ( thisRole.equals(role) ) {
        		Move mv = getMoveFromProposition(gmapp.get(g));
    			moves.add(mv);
    		}
    	}
        return moves;
    }

    /**
     * Computes the legal moves for role in state.
     */
    @Override
    public List<Move> getLegalMoves(MachineState state, Role role)
            throws MoveDefinitionException {
    	try {
    		// Set base propositions based on state
    		setBaseProps(state);

	    	// Return legal moves
    		return this.getLegalMoves(role);
	    }
    	catch(Exception e) {
    		throw new MoveDefinitionException(state, role);
    	}
    }

    /**
     * Computes the legal moves for role in the current propnet state.
     */
    public List<Move> getLegalMoves(Role role)
            throws MoveDefinitionException {
    	try {
	    	// Get all possible moves for role
	    	List<Move> moves = findActions(role);

	        // Set input propositions based on possible moves
	    	Map<GdlSentence, Proposition> gmapp = propNet.getInputPropositions();

	    	// Set all inputs to false
	    	for (Proposition p : gmapp.values()) {
	    		p.setValue(false);
	    	}

	    	// Set only our moves to true for check
	    	List<GdlSentence> movesGdl = toDoes(moves);
	    	for (GdlSentence g : movesGdl) {
	    		gmapp.get(g).setValue(true);
	    	}

	    	// Propagate
	    	propagatePropNet();

	        // Check legality
	    	List<Proposition> legalProps = new ArrayList<Proposition>(propNet.getLegalPropositions().get(role));
	    	Map<Proposition, Proposition> legalMap = propNet.getLegalInputMap();
	    	List<Move> legalMoves = new ArrayList<Move>();
	    	for ( Proposition lp : legalProps )
	    	{
	    		if (lp.getValue()) {
	        		Move mv = getMoveFromProposition(lp);
	        		legalMoves.add(mv);
	    		}
	    	}

	    	// Reset to false
	    	for (GdlSentence g : movesGdl) {
	    		gmapp.get(g).setValue(false);
	    	}

	    	// Return moves that are legal
	        return legalMoves;
	    }
    	catch(Exception e) {
    		throw new MoveDefinitionException(null, role);
    	}
    }

    /**
     * Computes the next state given state and the list of moves.
     */
    @Override
    public MachineState getNextState(MachineState state, List<Move> moves)
            throws TransitionDefinitionException {
    	try {
    		// Set base state
    		setBaseProps(state);

    		this.toNextState(moves);

    		return getStateFromBase();
    	}
    	catch(Exception e) {
    		throw new TransitionDefinitionException(state, moves);
    	}
    }

    /**
     * Gets the next state given propnet state.
     */
    public void toNextState(List<Move> moves)
            throws TransitionDefinitionException {
    	try {
    		// Set input propositions
	    	Map<GdlSentence, Proposition> gmapp = propNet.getInputPropositions();

	    	for (Proposition p : gmapp.values()) {
	    		p.setValue(false);
	    	}

	    	List<GdlSentence> movesGdl = toDoes(moves);
	    	for (GdlSentence g : movesGdl) {
	    		gmapp.get(g).setValue(true);
	    	}

    		// Propagate
    		propagatePropNet();

    		// Transition
    		transitionPropNet();
    	}
    	catch(Exception e) {
    		throw new TransitionDefinitionException(null, moves);
    	}
    }

    /**
     * This should compute the topological ordering of propositions.
     * Each component is either a proposition, logical gate, or transition.
     * Logical gates and transitions only have propositions as inputs.
     *
     * The base propositions and input propositions should always be exempt
     * from this ordering.
     *
     * The base propositions values are set from the MachineState that
     * operations are performed on and the input propositions are set from
     * the Moves that operations are performed on as well (if any).
     *
     * @return The order in which the truth values of propositions need to be set.
     */
    public List<Proposition> getOrdering()
    {
        // List to contain the topological ordering.
        List<Proposition> order = new LinkedList<Proposition>();

        // All of the components in the PropNet
        //List<Component> components = new ArrayList<Component>(propNet.getComponents());

        //for (Component c : components) {
        //	System.out.println(c.getClass().getName() );
        //}

        // All of the propositions in the PropNet.
        List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

        // Remove the base, input, and init propositions
        propositions.removeAll(propNet.getBasePropositions().values());
        propositions.removeAll(propNet.getInputPropositions().values());
        propositions.remove(propNet.getInitProposition());

        // Cycle through propositions
        List<Proposition> needOrder = new ArrayList<Proposition>(propositions);
        int counter = 0;
        while ( !needOrder.isEmpty() ) {
        	Proposition p = needOrder.get(counter);

        	// Check inputs to proposition.
        	List<Component> components = new ArrayList<Component>(p.getInputs());
        	while (!components.isEmpty()) {
        		Component thisC = components.get(0);

        		// If an ancestor is a proposition that still needs ordering, then keep
        		if ( thisC.getClass() == Proposition.class ) {
        			if ( needOrder.contains(thisC) ) {
        				break;
        			}
        		}
        		// If the ancestor is a transition, also ignore
        		else if ( thisC.getClass() != Transition.class ) {
        			// Add inputs to ancestor to the check
        			components.addAll( thisC.getInputs() );
        		}
        		components.remove(0);
        	}
        	// Proposition ancestors are all base, inputs, or already ordered, add
        	if ( components.isEmpty() ) {
        		order.add(p);
        		needOrder.remove(p);
        	}
        	// Otherwise continue checking
        	else {
        		counter++;
        	}
        	if (counter >= needOrder.size()) {
        		counter = 0;
        	}
        }

        //System.out.println( order.get(0).getInputs() );

        return order;
    }

    /*
     * Debugger: Set a state, then read it back to make sure it is the same.
     */
    public boolean checkStateFunctionality(MachineState state)
    {
    	setBaseProps(state);
    	MachineState back = getStateFromBase();
    	if ( state.equals(back) ) {
    		return true;
    	}
    	System.out.println("IN: " + state);
    	System.out.println("OUT: " + back);
    	return false;
    }

    /*
     * Helper function for propagating the propnet. Assumes inputs and base propositions are all already set.
     */
    private void propagatePropNet()
    {
    	for( Proposition p : ordering ) {
    		p.setValue(p.getSingleInput().getValue());
    	}
    }

    /*
     * Transition prop net
     */
    private void transitionPropNet()
    {
    	for (Proposition p : propNet.getBasePropositions().values()) {
    		p.setValue(p.getSingleInput().getValue());
    	}
    }

    /*
     * Sets base propositions based on state
     */
    public void setBaseProps(MachineState state)
    {
    	// Get mapping from gdl sentence to base proposition
    	Map<GdlSentence, Proposition> pmap = propNet.getBasePropositions();

    	// Set all base propositions to false
    	for ( Proposition p : pmap.values() ) {
    		p.setValue(false);
    	}

    	// Cycle through state definition, set all corresponding propositions to true.
    	for( GdlSentence g : state.getContents() ) {
    		pmap.get(g).setValue(true);
    	}
    }

    /* Already implemented for you */
    @Override
    public List<Role> getRoles() {
        return roles;
    }

    /* Helper methods */

    /**
     * The Input propositions are indexed by (does ?player ?action).
     *
     * This translates a list of Moves (backed by a sentence that is simply ?action)
     * into GdlSentences that can be used to get Propositions from inputPropositions.
     * and accordingly set their values etc.  This is a naive implementation when coupled with
     * setting input values, feel free to change this for a more efficient implementation.
     *
     * @param moves
     * @return
     */
    private List<GdlSentence> toDoes(List<Move> moves)
    {
        List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
        Map<Role, Integer> roleIndices = getRoleIndices();

        for (int i = 0; i < roles.size(); i++)
        {
            int index = roleIndices.get(roles.get(i));
            doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
        }
        return doeses;
    }

    /**
     * Takes in a Legal Proposition and returns the appropriate corresponding Move
     * @param p
     * @return a PropNetMove
     */
    public static Move getMoveFromProposition(Proposition p)
    {
        return new Move(p.getName().get(1));
    }

    /**
     * Helper method for parsing the value of a goal proposition
     * @param goalProposition
     * @return the integer value of the goal proposition
     */
    private int getGoalValue(Proposition goalProposition)
    {
        GdlRelation relation = (GdlRelation) goalProposition.getName();
        GdlConstant constant = (GdlConstant) relation.get(1);
        return Integer.parseInt(constant.toString());
    }

    /**
     * A Naive implementation that computes a PropNetMachineState
     * from the true BasePropositions.  This is correct but slower than more advanced implementations
     * You need not use this method!
     * @return PropNetMachineState
     */
    public MachineState getStateFromBase()
    {
        Set<GdlSentence> contents = new HashSet<GdlSentence>();
        for (Proposition p : propNet.getBasePropositions().values())
        {
        	// Removed this line since this gets handled by the transition function now.
            //p.setValue(p.getSingleInput().getValue());
            if (p.getValue())
            {
                contents.add(p.getName());
            }

        }
        return new MachineState(contents);
    }

    public int getNumComponents()
    {
    	return propNet.getComponents().size();
    }

    /**
     * Returns a random joint move from among all the possible joint moves in
     * the current propnet state in which the given role makes the given move.
     */
    public List<Move> getRandomJointMove() throws MoveDefinitionException
    {
    	return getRandomJointMove(getStateFromBase());
    }
}