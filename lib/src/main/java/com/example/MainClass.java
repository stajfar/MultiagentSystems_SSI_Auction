package com.example;

import FIPA.DateTime;
import jade.content.ContentElementList;
import jade.content.ContentManager;
import jade.content.lang.Codec;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.Agent;
import java.util.*;
import java.util.regex.*;
import java.io.*;

import jade.core.*;
import jade.core.behaviours.*;
import jade.domain.*;
import jade.domain.FIPAAgentManagement.*;
import jade.lang.acl.*;
import jade.proto.*;
import javafx.geometry.Pos;
import sun.util.BuddhistCalendar;


public class MainClass extends Agent {




    protected final String CLEAR_MESSAGE = "!clear!";
    protected final int MAX_WAIT_TIME_FOR_RESPONSES_MS = 5000;



    //variables
    protected ArrayList<AID> buyer_robots = new ArrayList<AID>();
    protected int active_auction_counter = 0;
    protected Properties properties = new Properties();
    ArrayList<Position> availableTasks=new ArrayList<>();
    Position agentInitialPosition=new Position();
    Position bestTaskCostPair=new Position();



    String taskCoordinations;


    public static void main(String[] args) {    }


    //Agent lifecycle methods
    protected void setup() {
        log("AuctionAgent: Starting up: "+ getAID().getName());



        // Get the list of the tasks to bid as a start-up argument
        Object[] args = getArguments();
        if (args != null && args.length > 0) {

             String tasksToBidOn = (String) args[0];
             availableTasks=separateIntroducedTasks(tasksToBidOn);

             agentInitialPosition=getAgentInitialPosition((String) args[1]);

            log("Agent initial position"+ agentInitialPosition);
            log("Trying to bid on tasks: "+tasksToBidOn);

            //determine the cost of each task for this agent (distance of points)
             bestTaskCostPair= calculateMinTaskCost(agentInitialPosition,availableTasks);
        }
        else {
            // Make the agent terminate immediately
            log("No Task to bid specified");
            doDelete();
        }

        // Add the behaviour serving calls for price from buyer agents
        addBehaviour(new CallForOfferServer());

        // Register the task-selling service in the yellow pages
        registerTaskBuyingServiceToThisAgent();

        //finding the list of task-buyer agents (updates each 6000)
        // updates buyer_robots variable
        findingTaskBuyerAgents();

        //Agent starts to bid on the task that currently has the minimum cost
        if(bestTaskCostPair.cost !=null)
        bidOneTaskWithMinCost();


    }

    private void bidOneTaskWithMinCost() {
        //add ticker behavior to bid on tasks
        // Add the behaviour serving calls for price from buyer agents
        int random=1000 + (int)(Math.random() * 1500);
        addBehaviour(new bidOnTask(this,random));
    }

    private Position calculateMinTaskCost(Position agentInitialPosition, ArrayList<Position> availableTasks) {
        Position bestTaskCostPair=new Position();
        double lowestCost= Float.POSITIVE_INFINITY;
        for(Position taskPosition: availableTasks){
           double cost= Math.sqrt(Math.pow((agentInitialPosition.x-taskPosition.x), 2) + Math.pow((agentInitialPosition.y-taskPosition.y), 2));
            taskPosition.cost=cost;
            if(cost < lowestCost){
                lowestCost=cost;
                bestTaskCostPair.cost=lowestCost;
                bestTaskCostPair.x=taskPosition.x;
                bestTaskCostPair.y=taskPosition.y;
            }
        }
        return bestTaskCostPair;
    }

    private Position getAgentInitialPosition(String agentPos) {
        Position agentPosition=new Position();
        agentPosition.x=Integer.parseInt(agentPos.split("-")[0]);
        agentPosition.y=Integer.parseInt(agentPos.split("-")[1]);

        return agentPosition;
    }

    private ArrayList<Position> separateIntroducedTasks(String tasksToBidOn) {
        ArrayList<Position> separatedTasksArrayList=new ArrayList<>();

        String[] separatedTasksArray=tasksToBidOn.split("_");
        for(String taskPosition: separatedTasksArray){
            Position task=new Position();
            task.x=Integer.parseInt(taskPosition.split("-")[0]);
            task.y=Integer.parseInt(taskPosition.split("-")[1]);
            separatedTasksArrayList.add(task);
        }
        return separatedTasksArrayList;
    }

    private void findingTaskBuyerAgents() {
        try {
            log("finding neighbor Agents");
            addBehaviour(new TickerBehaviour(this, 20000) {
                protected void onTick() {
                    // Update the list of seller agents
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("Task-Buying");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        buyer_robots.clear();
                        for (int i = 0; i < result.length; ++i) {
                            buyer_robots.add(result[i].getName());
                        }
                        log("I have " + buyer_robots.size() + " neighbors");
                    }
                    catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                }
            } );

            log("Adding behaviours.");


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void registerTaskBuyingServiceToThisAgent() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Task-Buying");
        sd.setName(getLocalName()+"-Task-Buying");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }


    /**
     * Pre-death clean up called by JADE
     */
    protected void takeDown() {
        log("AuctionAgent says Goodbye!");
    }

    private void log(String s) {
        System.out.println(String.format("%8s %8s", getAID().getLocalName(), s));
    }



    private class bidOnTask extends TickerBehaviour {

        public bidOnTask(Agent agent, int random) {
            super(agent, random + 6000);
        }


        protected void onTick() {
            bestTaskCostPair=calculateMinTaskCost(agentInitialPosition,availableTasks);
            myAgent.addBehaviour(new TaskNegotiator(agentInitialPosition, bestTaskCostPair, this));

        }
    }


    public ACLMessage cfp = new ACLMessage(ACLMessage.CFP); // variable needed to the ContractNetInitiator constructor
    private class TaskNegotiator extends ContractNetInitiator {
        Position agentInitialPos;
        Position bestTaskCostPair;
        bidOnTask bidOnTaskTicker;



        public TaskNegotiator(Position agentInitialPos1, Position bestTaskCostPair1, bidOnTask bidOnTaskTicker1) {
            super(MainClass.this,cfp);
            agentInitialPos=agentInitialPos1;
            bestTaskCostPair=bestTaskCostPair1;
            bidOnTaskTicker=bidOnTaskTicker1;

        }

        protected Vector prepareCfps(ACLMessage cfp)  {
            cfp.clearAllReceiver();
            String message=createTaskString(agentInitialPos,bestTaskCostPair);
            cfp.setContent(message);
            //cfp.setReplyByDate(new Date(System.currentTimeMillis() + MAX_WAIT_TIME_FOR_RESPONSES_MS));

            for (int i = 0; i < buyer_robots.size(); ++i) {
                // only send to other robots, as this robot knows about its own cost
                if(! myAgent.getAID().equals((AID)buyer_robots.get(i))) {
                    cfp.addReceiver((AID) buyer_robots.get(i));
                }
            }
            Vector v = new Vector();
            v.add(cfp);
           //sent the message to all receiver agents
            log("sending message.......");
            return v;
        }

        protected void handleInform(ACLMessage inform) {
           log("handle infrom");
        }







        protected void handleAllResponses(Vector responses, Vector acceptances) {

            for (int i = 0; i < responses.size(); i++) {
                ACLMessage r = (ACLMessage) responses.get(i);
                if (r.getPerformative() == ACLMessage.PROPOSE) {
                    log("Allocated task: "+ r.getContent());
                    //get the position of allocated task from given content
                    Position pos=new Position();
                    pos.x=Integer.parseInt( r.getContent().split("-")[0]);
                    pos.y=Integer.parseInt( r.getContent().split("-")[1]);
                    // remove the task from available task list
                    for(Position posi: availableTasks){
                        if(posi.x == pos.x && posi.y == pos.y)
                            pos=posi;
                    }
                    availableTasks.remove(pos);
                    //update the position of task
                    agentInitialPosition.x=pos.x;
                    agentInitialPosition.y=pos.y;
                    //update the cost of remaining tasks
                    bestTaskCostPair= calculateMinTaskCost(agentInitialPosition,availableTasks);
                    ACLMessage reply=r.createReply();
                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);

                }
            }




        }







        private String createTaskString(Position agentInitialPos, Position bestTaskCostPair) {
            String output  = String.format("%d-%d_%f_%d-%d", agentInitialPos.x, agentInitialPos.y,bestTaskCostPair.cost,bestTaskCostPair.x,bestTaskCostPair.y);
            return output;

        }

    }



   // this agent listens to all the incomming message to handle recieved message
    private class CallForOfferServer extends ContractNetResponder {
        ArrayList<String> taskProposalCollection=new ArrayList<>();
        Map<AID,ACLMessage> senderAgents=new HashMap<>();


        CallForOfferServer() {
            super(MainClass.this, MessageTemplate.MatchAll());
        }

        protected ACLMessage handleCfp(ACLMessage cfp) throws RefuseException, FailureException, NotUnderstoodException {
            // CFP Message received. Process it
            ACLMessage reply = cfp.createReply();


      if (cfp.getPerformative() != ACLMessage.CFP) {
    	  reply.setPerformative(ACLMessage.FAILURE);
    	  log("Not a ACLmessage.cfp, so remove it");
    	  reinit();
      }
      else {
                try {
                   // if(!senderAgents.containsKey(cfp.getSender()) && senderAgents.size() < buyer_robots.size()){
                    //    senderAgents.put(cfp.getSender(),cfp);
                   // }else
                    {
                        //now, we are sure that all the bids are collected from all agents
                        taskProposalCollection.add(cfp.getSender().getName() + ": " + cfp.getContent());
                        log("message recieved from: " + cfp.getSender() + " " + cfp.getContent());
                        //collect all proposals form given messages
                        //boolean test2=false;

                        double recievedCost = Double.parseDouble(cfp.getContent().split("_")[1]);
                        Position minlocalCostTask = new Position();
                        minlocalCostTask.cost = 10000.00;
                        for (Position localTaskPos : availableTasks) {
                            if (localTaskPos.cost < minlocalCostTask.cost)
                                minlocalCostTask = localTaskPos;
                        }

                        log("recievedCost "+recievedCost);
                        log(" minlocalCostTask.cost "+ minlocalCostTask.cost);

                        if (recievedCost < minlocalCostTask.cost) {
                            //the winner is the other agent
                            //propose the task to other agent and remove the task from local task list
                            reply.setPerformative(ACLMessage.PROPOSE);
                            reply.setContent(cfp.getContent().split("_")[2]);//this is the position of the received task


                            Position position = new Position();
                            position.x = Integer.parseInt(cfp.getContent().split("_")[2].split("-")[0]);
                            position.y = Integer.parseInt(cfp.getContent().split("_")[2].split("-")[1]);
                            for (Position pos : availableTasks) {
                                if (pos.x == position.x && pos.y == position.y) {
                                    position = pos;
                                }
                            }

                            availableTasks.remove(position);

                        } else {
                            //this agent is the winner
                            //propose other agent to remove the task from its task list only
                            //update this agent position
                                    /*remove from task list
                                    reply.setPerformative(ACLMessage.INFORM);// or reject
                                    reply.setContent(minlocalCostTask.x +"-"+minlocalCostTask.y);

                                    availableTasks.remove(minlocalCostTask);
                                    agentInitialPosition.x=minlocalCostTask.x;
                                    agentInitialPosition.y=minlocalCostTask.y;
                                    log("Allocated task: ["+ minlocalCostTask.x +" "+minlocalCostTask.y+"]");
                                    //update the costs to remaining tasks w.r.t new position
                                    calculateMinTaskCost(agentInitialPosition,availableTasks);
                                    */
                            reply.setPerformative(ACLMessage.REFUSE);
                            log("cfp refused");

                        }

                        //this task is available in tasks list
                        // test=true;


                    }
                }

                catch (Exception e) {
                    e.printStackTrace();
                    reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                    log("cfp not understood");
                }
            }
            //System.out.println(myAgent.getLocalName()+"RX"+cfp+"\nTX"+reply+"\n\n");
            // myGui.notifyUser(reply.getPerformative() == ACLMessage.PROPOSE ? "Sent Proposal to sell at "+ price : "Refused Proposal as the book is not for sale");
            return reply;
        }

       protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
           log("handle acccept proposal");
           ACLMessage inform=accept.createReply();
           inform.setPerformative(ACLMessage.INFORM);
           return inform;
       }


    }





}
