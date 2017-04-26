package com.example;


import jade.core.Agent;
import java.util.*;
import jade.core.*;
import jade.core.behaviours.*;
import jade.domain.*;
import jade.domain.FIPAAgentManagement.*;
import jade.lang.acl.*;




public class MainClass2 extends Agent {

    //variables
    protected ArrayList<AID> buyer_robots = new ArrayList<AID>();
    ArrayList<Position> availableTasks=new ArrayList<>();
    Position agentInitialPosition=new Position();
    Position bestTaskCostPair=new Position();



    public static void main(String[] args) {
        
    }


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
        log("Adding behaviours.");
        // Add the behaviour serving calls for price from buyer agents
         addBehaviour(new OfferRequestsServer());

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
            addBehaviour(new TickerBehaviour(this, 10000) {
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
            super(agent, 20000);
        }

        protected void onTick() {
            if(availableTasks.size() == 0)
            {
                // Make the agent terminate immediately
                log("No Task to bid specified");
                doDelete();
            }
            bestTaskCostPair=calculateMinTaskCost(agentInitialPosition,availableTasks);

            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);

            cfp.clearAllReceiver();
            String message=createTaskString(agentInitialPosition,bestTaskCostPair);
            cfp.setContent(message);


            for (int i = 0; i < buyer_robots.size(); ++i) {
                // only send to other robots, as this robot knows about its own cost
                if(! myAgent.getAID().equals((AID)buyer_robots.get(i))) {
                    cfp.addReceiver((AID) buyer_robots.get(i));
                }
            }
            myAgent.send(cfp);

        }

        private String createTaskString(Position agentInitialPos, Position bestTaskCostPair) {
            String output  = String.format("%d-%d_%f_%d-%d", agentInitialPos.x, agentInitialPos.y,bestTaskCostPair.cost,bestTaskCostPair.x,bestTaskCostPair.y);
            return output;
        }

    }



    private class OfferRequestsServer extends CyclicBehaviour {
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null && availableTasks.size() >0) {
                // CFP Message received. Process it
                ACLMessage reply = msg.createReply();

                if (msg.getPerformative() == ACLMessage.PROPOSE) {
                    log("Allocated task: "+ msg.getContent());
                    //get the position of allocated task from given content
                    Position pos=new Position();
                    pos.x=Integer.parseInt( msg.getContent().split("-")[0]);
                    pos.y=Integer.parseInt( msg.getContent().split("-")[1]);
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
                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                    reply.setContent(pos.x+"-"+pos.y);

                }else if(msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    if(msg.getContent() != null) {
                        log("Other agent Accepted proposal, updating task list");
                        Position position = new Position();
                        position.x = Integer.parseInt(msg.getContent().split("-")[0]);
                        position.y = Integer.parseInt(msg.getContent().split("-")[1]);
                        for (Position pos : availableTasks) {
                            if (pos.x == position.x && pos.y == position.y) {
                                position = pos;
                            }
                        }
                        availableTasks.remove(position);
                    }
                }
                else if(msg.getPerformative() ==ACLMessage.REFUSE){
                    return;
                }
                else {

                    //now, we are sure that all the bids are collected from all agents
                    double recievedCost = Double.parseDouble(msg.getContent().split("_")[1]);
                    Position minlocalCostTask = new Position();
                    minlocalCostTask.cost = 10000.00;
                    for (Position localTaskPos : availableTasks) {
                        if (localTaskPos.cost < minlocalCostTask.cost)
                            minlocalCostTask = localTaskPos;
                    }

                    //log("recievedCost " + recievedCost);
                   // log(" minlocalCostTask.cost " + minlocalCostTask.cost);

                    if (recievedCost < minlocalCostTask.cost) {
                        //the winner is the other agent
                        //propose the task to other agent and remove the task from local task list
                        log("Received cost < local cost, proposing task to other agent");
                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent(msg.getContent().split("_")[2]);//this is the position of the received task

                    } else {
                        //this agent has better task-cost pair
                        //refuse the cfp
                        reply.setPerformative(ACLMessage.REFUSE);
                        log("cfp refused!");

                    }
                }
                myAgent.send(reply);

            }
            else {
                block();
            }
        }
    } // End of inner class OfferRequestsServer


}
