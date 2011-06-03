package uk.ac.dmu.iesd.cascade.context;


import java.util.*;
import repast.simphony.engine.schedule.*;
import repast.simphony.essentials.RepastEssentials;
import repast.simphony.random.*;
import repast.simphony.space.graph.*;
import uk.ac.dmu.iesd.cascade.Consts;
import uk.ac.dmu.iesd.cascade.util.ArrayUtils;
import static repast.simphony.essentials.RepastEssentials.*;


/**
 * @author J. Richard Snape
 * @author Babak Mahdavi
 * @version $Revision: 1.1 $ $Date: 2011/05/18 12:00:00 $
 * 
 * Version history (for intermediate steps see Git repository history
 * 
 * 1.0 - Initial split of categories of prosumer from the abstract class representing all prosumers
 * 1.1 - 
 * 
 */
public class HouseholdProsumer extends ProsumerAgent{

	/*
	 * Configuration options
	 * 
	 * All are set false initially - they can be set true on instantiation to
	 * allow fine-grained control of agent properties
	 */

	boolean hasCHP = false;
	boolean hasAirSourceHeatPump = false;
	boolean hasGroundSourceHeatPump = false;
	boolean hasWind = false;
	boolean hasHydro = false;
	// Thermal Generation included so that Household can represent
	// Biomass, nuclear or fossil fuel generation in the future
	// what do we think?
	boolean hasThermalGeneration = false;
	boolean hasPV = false;
	boolean hasSolarWaterHeat = false;
	boolean hasElectricalWaterHeat = false;
	boolean hasElectricalSpaceHeat = false;
	boolean hasElectricVehicle = false;
	boolean hasElectricalStorage = false; // Do we need to break out various storage technologies?
	boolean hasHotWaterStorage = false;
	boolean hasSpaceHeatStorage = false;


	/*
	 * the rated power of the various technologies / appliances we are interested in
	 * 
	 * Do not initialise these initially.  They should be initialised when an
	 * instantiated agent is given the boolean attribute which means that they
	 * have access to one of these technologies.
	 */
	float ratedPowerCHP;
	float ratedPowerAirSourceHeatPump;
	float ratedPowerGroundSourceHeatPump;
	float ratedPowerWind;
	float ratedPowerHydro;
	float ratedPowerThermalGeneration;
	float ratedPowerPV;
	float ratedPowerSolarWaterHeat;
	float ratedPowerElectricalWaterHeat;
	float ratedPowerElectricalSpaceHeat;
	float ratedCapacityElectricVehicle; // Note kWh rather than kW
	float ratedCapacityElectricalStorage;   // Note kWh rather than kW
	float ratedCapacityHotWaterStorage;
	float ratedCapacitySpaceHeatStorage; // Note - related to thermal mass


	// For Households' heating requirements
	float buildingHeatCapacity;
	float buildingHeatLossRate;
	float buildingTemperatureSetPoint;
	float spaceTemperature;
	float waterTemperature;

	/*
	 * temperature control parameters
	 */
	float setPoint;
	float minSetPoint;  // The minimum temperature for this Household's building in centigrade (where relevant)
	float maxSetPoint;  // The maximum temperature for this Household's building in centigrade (where relevant)
	float currentInternalTemp;
	
	//Occupancy information
	int numOccupants;
	int numAdults;
	int numChildren;
	int numTeenagers;
	int[] occupancyProfile;


	/*
	 * Specifically, a household may have a certain percentage of demand
	 * that it believes is moveable and / or a certain maximum time shift of demand
	 */
	float percentageMoveableDemand;  // This can be set constant, or calculated from available appliances
	int maxTimeShift; // The "furthest" a demand may be moved in time.  This can be constant or calculated dynamically.
	/*
	 * Behavioural properties
	 */
	// Learner adoptSmartMeterLearner;
	// Learner adoptSmartControlLearner;
	// Learner consumptionPatternLearner;
	float transmitPropensitySmartControl;
	float transmitPropensityProEnvironmental;
	float visibilityMicrogen;

	
	/*
	 * This may or may not be used, but is a threshold cost above which actions
	 * take place for the household
	 */
	float costThreshold;

	/**
	 * Constructs a household prosumer agent with the context in which is created and its
	 * base demand.
	 * @param context the context in which this agent is situated
	 * @param baseDemand an array containing the base demand  
	 */
	public HouseholdProsumer(CascadeContext context, float[] baseDemand) {
		super(context);
		this.percentageMoveableDemand = (float) RandomHelper.nextDoubleFromTo(0, 0.5);
		this.ticksPerDay = context.getTickPerDay();
		if (baseDemand.length % ticksPerDay != 0)
		{
			System.err.println("baseDemand array not a whole number of days");
			System.err.println("Will be truncated and may cause unexpected behaviour");
		}
		this.baseDemandProfile = new float [baseDemand.length];
		System.arraycopy(baseDemand, 0, this.baseDemandProfile, 0, baseDemand.length);
		//Initialise the smart optimised profile to be the same as base demand
		//smart controller will alter this
		this.smartOptimisedProfile = new float [baseDemand.length];
		System.arraycopy(baseDemand, 0, this.smartOptimisedProfile, 0, smartOptimisedProfile.length);
	}


	/**
	 * Returns a string representing the state of this agent. This 
	 * method is intended to be used for debugging purposes, and the 
	 * content and format of the returned string should include the states (variables/parameters)
	 * which are important for debugging purpose.
	 * The returned string may be empty but may not be <code>null</code>.
	 * 
	 * @return  a string representation of this agent's state parameters
	 */
	protected String paramStringReport(){
		String str="";
		return str;

	}


	/**
	 * This method defines how this specific object behaves (what it does)
	 * at at a given scheduled time throughout the simulation
	 */
	@ScheduledMethod(start = 1, interval = 1, shuffle = true)
	public void step() {
		//System.out.println("HHProsumer step called: "+ RepastEssentials.GetTickCount());

		// Define the return value variable.  Set this false if errors encountered.
		boolean returnValue = true;

		// Note the simulation time if needed.
		// Note - Repast can cope with fractions of a tick (a double is returned)
		// but I am assuming here we will deal in whole ticks and alter the resolution should we need
		int time = (int) RepastEssentials.GetTickCount();
		int timeOfDay = (time % ticksPerDay);
		CascadeContext myContext = this.getContext();
		

		checkWeather(time);

		//Do all the "once-per-day" things here
		if (timeOfDay == 0)
		{
			inelasticTotalDayDemand = calculateFixedDayTotalDemand(time);
			if (hasSmartControl){
				smartControlLearn(time);
			}
		}

		if (hasSmartControl){
			setNetDemand(smartDemand(time));
		}
		else if (hasSmartMeter && exercisesBehaviourChange) {
			//learnBehaviourChange();
			setNetDemand(evaluateBehaviour(time));
			//learnSmartAdoptionDecision(time);
		}
		else
		{
			//No adaptation case
			setNetDemand(baseDemandProfile[time % baseDemandProfile.length] - currentGeneration());

			learnSmartAdoptionDecision(time);
		}
		
		
		//----- Babak Network test ----------------------------------------------
		Network costumerNetwork = FindNetwork("BabakTestNetwork");
		Iterable costumersIter = costumerNetwork.getInEdges(this);
		
		for (Object thisConn: costumersIter)
		{
			RepastEdge myConn = ((RepastEdge) thisConn);
			AggregatorAgent agg = (AggregatorAgent) myConn.getSource();
			System.out.println(this.toString()+ " is provided by: "+ agg.toString()); 

		}
		//System.out.println("------------------");
		// -- End of test --------------------------------------------------------



		// Return (this will be false if problems encountered).
		//return returnValue;

	}

	


	public float getUnadaptedDemand(){
		// Cope with tick count being null between project initialisation and start.
		int index = Math.max(((int) RepastEssentials.GetTickCount() % baseDemandProfile.length), 0);
		return (baseDemandProfile[index]) - currentGeneration();
	}


	public float getSetPoint() {
		return setPoint;
	}
	
	/**
	 * @return
	 */
	private float currentGeneration() {
		float returnAmount = 0;

		returnAmount = returnAmount + CHPGeneration() + windGeneration() + hydroGeneration() + thermalGeneration() + PVGeneration();
		if (Consts.DEBUG)
		{
			if (returnAmount != 0)
			{
				System.out.println("Generating " + returnAmount);
			}
		}
		return returnAmount;
	}

	/**
	 * @return
	 */
	private float PVGeneration() {
		// TODO Auto-generated method stub
		return 0;
	}

	/**
	 * @return
	 */
	private float thermalGeneration() {
		// TODO Auto-generated method stub
		return 0;
	}

	/**
	 * @return
	 */
	private float hydroGeneration() {
		// TODO Auto-generated method stub
		return 0;
	}

	/**
	 * @return
	 */
	private float windGeneration() {
		if(hasWind){
			//TODO: get a realistic model of wind production - this just linear between 
			//5 and 25 metres per second, zero below, max power above
			return (Math.max((Math.min(getWindSpeed(),25) - 5),0))/20 * ratedPowerWind;
		}
		else
		{
			return 0;
		}
	}

	/**
	 * @return
	 */
	private float CHPGeneration() {
		// TODO Auto-generated method stub
		return 0;
	}

	/**
	 * @param time
	 * @return float giving sum of baseDemand for the day.
	 */
	private float calculateFixedDayTotalDemand(int time) {
		int baseProfileIndex = time % baseDemandProfile.length;
		return ArrayUtils.sum(Arrays.copyOfRange(baseDemandProfile,baseProfileIndex,baseProfileIndex+ticksPerDay - 1));
	}

	/*
	 * Logic helper methods
	 */

	/*
	 * Evaluates the net demand mediated by the prosumers behaviour in a given half hour.
	 * 
	 * NOTE: 	As implemented - this method does not enforce total demand parity over a given day (i.e.
	 * 			integral of netDemand over a day is not necessarily constant.
	 * 
	 * @param 	int time	- the simulation time in ticks at which to evaluate this prosumer's behaviour
	 * @return 	float myDemand		- the demand for this half hour, mediated via behaviour change
	 */
	private float evaluateBehaviour(int time)
	{
		float myDemand;
		int timeSinceSigValid = time - predictionValidTime;

		//As a basic strategy ("pass-through"), we set the demand now to
		//basic demand as of now.
		myDemand = baseDemandProfile[time % baseDemandProfile.length];

		// Adapt behaviour somewhat.  Note that this does not enforce total demand the same over a day.
		// Note, we can only moderate based on cost signal
		// if we receive it (i.e. if we have smart meter)
		// TODO: may have to refine this - do we differentiate smart meter and smart display - i.e. whether receive only or Tx/Rx
		if(hasSmartMeter && predictedCostSignalLength > 0)
		{
			float predictedCostNow = predictedCostSignal[timeSinceSigValid % predictedCostSignalLength];
			if ( predictedCostNow > costThreshold){
				//Infinitely elastic version (i.e. takes no account of percenteageMoveableDemand
				// TODO: Need a better logging system than this - send logs with a level and output to
				// console or file.  Can we use log4j?
				if (Consts.DEBUG)
				{
					System.out.println("Agent " + this.agentID + "Changing demand at time " + time + " with price signal " + (predictedCostNow - costThreshold) + " above threshold");
				}
				myDemand = myDemand * (1 - percentageMoveableDemand * (1 - (float) Math.exp( - ((predictedCostNow - costThreshold) / costThreshold))));

			}
		}


		return myDemand;
	}

	/*
	 * Evaluates the net demand mediated by smart controller behaviour at a given tick.
	 * 
	 * NOTE: 	As implemented - this method enforces total demand parity over a given day (i.e.
	 * 			integral of netDemand over a day is not necessarily constant.
	 * 
	 * @param 	int time	- the simulation time in ticks at which to evaluate this prosumer's behaviour
	 * @return 	float myDemand		- the demand for this half hour, mediated via behaviour change
	 */
	private float smartDemand(int time)
	{
		//Very simple function at the moment - just return the smart profile for this time
		//Previously defined by smartControlLearn()
		float myDemand;
		myDemand = smartOptimisedProfile[time % smartOptimisedProfile.length];
		return myDemand;
	}

	private void learnBehaviourChange()
	{
		// TODO: Implement the behavioural (social?) learning in here
	}

	private void smartControlLearnFlat(int time)
	{
		// simplest smart controller implementation - perfect division of load through the day
		float moveableLoad = inelasticTotalDayDemand * percentageMoveableDemand;
		float [] daysCostSignal = new float [ticksPerDay];
		float [] daysOptimisedDemand = new float [ticksPerDay];
		System.arraycopy(predictedCostSignal, time - this.predictionValidTime, daysCostSignal, 0, ticksPerDay);

		System.arraycopy(smartOptimisedProfile, time % smartOptimisedProfile.length, daysOptimisedDemand, 0, ticksPerDay);

		float [] tempArray = ArrayUtils.mtimes(daysCostSignal, daysOptimisedDemand);

		float currentCost = ArrayUtils.sum(tempArray);
		// Algorithm to minimise this whilst satisfying constraints of
		// maximum movable demand and total demand being inelastic.

		float movedLoad = 0;
		float movedThisTime = -1;
		float swapAmount = -1;
		while (movedLoad < moveableLoad && movedThisTime != 0)
		{
			Arrays.fill(daysOptimisedDemand, inelasticTotalDayDemand / ticksPerDay);
			movedThisTime = 0;
			tempArray = ArrayUtils.mtimes(daysOptimisedDemand, daysCostSignal);			                   	                                             
		}
		System.arraycopy(daysOptimisedDemand, 0, smartOptimisedProfile, time % smartOptimisedProfile.length, ticksPerDay);
		if (Consts.DEBUG)
		{
			if (ArrayUtils.sum(daysOptimisedDemand) != inelasticTotalDayDemand)
			{
				//TODO: This always gets triggerd - I wonder if the "day" i'm taking
				//here and in the inelasticdemand method are "off-by-one"
				System.out.println("optimised signal has varied the demand !!! In error !" + (ArrayUtils.sum(daysOptimisedDemand) - inelasticTotalDayDemand));
			}

			System.out.println("Saved " + (currentCost - ArrayUtils.sum(tempArray)) + " cost");
		}
	}
	
	private void smartControlLearn(int time)
	{
		// smart device (optimisation) learning in here
		// in lieu of knowledge of what can "switch off" and "switch on", we assume that
		// the percentage moveable of the day's consumption is what may be time shifted
		float moveableLoad = inelasticTotalDayDemand * percentageMoveableDemand;
		float [] daysCostSignal = new float [ticksPerDay];
		float [] daysOptimisedDemand = new float [ticksPerDay];
		//System.out.println("predictedCostSignal "+predictedCostSignal+" time "+time+ " predictionValidTime "+predictionValidTime+" daysCostSignal "+ daysCostSignal +" ticksPerDay "+ticksPerDay);
		//if (predictedCostSignal != null)
		System.arraycopy(predictedCostSignal, time - this.predictionValidTime, daysCostSignal, 0, ticksPerDay);

		System.arraycopy(smartOptimisedProfile, time % smartOptimisedProfile.length, daysOptimisedDemand, 0, ticksPerDay);

		float [] tempArray = ArrayUtils.mtimes(daysCostSignal, daysOptimisedDemand);

		float currentCost = ArrayUtils.sum(tempArray);
		// Algorithm to minimise this whilst satisfying constraints of
		// maximum movable demand and total demand being inelastic.

		float movedLoad = 0;
		float movedThisTime = -1;
		float swapAmount = -1;
		while (movedLoad < moveableLoad && movedThisTime != 0)
		{
			int maxIndex = ArrayUtils.indexOfMax(tempArray);
			int minIndex = ArrayUtils.indexOfMin(tempArray);
			swapAmount = (daysOptimisedDemand[minIndex] + daysOptimisedDemand[maxIndex]) / 2;
			movedThisTime = ((daysOptimisedDemand[maxIndex] - daysOptimisedDemand[minIndex]) / 2);
			movedLoad = movedLoad + movedThisTime;
			daysOptimisedDemand[maxIndex] = swapAmount;
			daysOptimisedDemand[minIndex] = swapAmount;
			if (Consts.DEBUG)
			{
				System.out.println(agentID + " moving " + movedLoad + "MaxIndex = " + maxIndex + " minIndex = " + minIndex + Arrays.toString(tempArray));
			}
			tempArray = ArrayUtils.mtimes(daysOptimisedDemand, daysCostSignal);			                   	                                             
		}
		System.arraycopy(daysOptimisedDemand, 0, smartOptimisedProfile, time % smartOptimisedProfile.length, ticksPerDay);
		if (Consts.DEBUG)
		{
			if (ArrayUtils.sum(daysOptimisedDemand) != inelasticTotalDayDemand)
			{
				//TODO: This always gets triggerd - I wonder if the "day" i'm taking
				//here and in the inelasticdemand method are "off-by-one"
				System.out.println("optimised signal has varied the demand !!! In error !" + (ArrayUtils.sum(daysOptimisedDemand) - inelasticTotalDayDemand));
			}

			System.out.println("Saved " + (currentCost - ArrayUtils.sum(tempArray)) + " cost");
		}
	}

	private void learnSmartAdoptionDecisionRemoveAll(int time)
	{
		hasSmartControl = false;
		return;
	}
	
	private void learnSmartAdoptionDecision(int time)
	{
		
		// TODO: implement learning whether to adopt smart control in here
		// Could be a TpB based model.
		float inwardInfluence = 0;
		float internalInfluence = 0;
		Iterable socialConnections = FindNetwork("socialNetwork").getInEdges(this);
		// Get social influence - note communication is not every tick
		// hence the if clause
		if ((time % (21 * ticksPerDay)) == 0)
		{

			for (Object thisConn: socialConnections)
			{
				RepastEdge myConn = ((RepastEdge) thisConn);
				if (((HouseholdProsumer) myConn.getSource()).hasSmartControl)
				{

					inwardInfluence = inwardInfluence + (float) myConn.getWeight() * ((HouseholdProsumer) myConn.getSource()).transmitPropensitySmartControl;
				}
			}
		}

		float decisionCriterion = inwardInfluence + internalInfluence;
		if(decisionCriterion > (Double) GetParameter("smartControlDecisionThreshold")) 
		{
			hasSmartControl = true;
		}
	}

	
}
