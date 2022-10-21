package amazed.solver;

import amazed.maze.Maze;

import java.sql.SQLOutput;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */


public class ForkJoinSolver
        extends SequentialSolver {
    private AtomicBoolean goalReached = new AtomicBoolean();
    /**
     * is a list of all threads we create in order to add path-results later on
     */
    private ArrayList<ForkJoinSolver> forks = new ArrayList<>();
    /**
     * makes visited thread-safe, as some players may move to same node simultaneously because of race conditions
     */
    private ConcurrentSkipListSet visited = new ConcurrentSkipListSet();
    /**
     * startpos created for start of each thread
     */
    private int startPos = start;

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal.
     *
     * @param maze the maze to be searched
     */
    public ForkJoinSolver(Maze maze) {
        super(maze);
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze      the maze to be searched
     * @param forkAfter the number of steps (visited nodes) after
     *                  which a parallel task is forked; if
     *                  <code>forkAfter &lt;= 0</code> the solver never
     *                  forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter) {
        this(maze);
        this.forkAfter = forkAfter;
    }

    /**
     * Created to send more parameters to each thread
     * @param maze - maze to be explored
     * @param startPos - start position for every thread as these are not the same as start position for the maze
     * @param visited - set of all visited nodes
     * @param predecessor - map of predecessors, where key returns previous node
     * @param goalReached - atomic boolean in case a thread has found the goal
     */
    public ForkJoinSolver(
            Maze maze, int startPos, ConcurrentSkipListSet visited, Map<Integer, Integer> predecessor, AtomicBoolean goalReached) {
        this(maze);
        this.startPos = startPos;
        this.visited = visited;
        this.predecessor = predecessor;
        this.goalReached = goalReached;
    }

    /**
     * Searches for and returns the path, as a list of node
     * identifiers, that goes from the start node to a goal node in
     * the maze. If such a path cannot be found (because there are no
     * goals, or all goals are unreacheable), the method returns
     * <code>null</code>.
     *
     * @return the list of node identifiers from the start node to a
     * goal node in the maze; <code>null</code> if such a path cannot
     * be found.
     */
    @Override
    public List<Integer> compute() {
        return parallelSearch();
    }

    private List<Integer> parallelSearch() {
        int player = maze.newPlayer(startPos);
        int currentNode;
        frontier.push(startPos);

        /**
         *If no nodes are to be visited and a player has found the goal, the solver will stop running
         */
        while (!frontier.empty() && !goalReached.get()) {
            currentNode = frontier.pop();

            /**
             *if any node finds the goal, the global atomic variable goalReached is set to true and the winning path
             * is returned
             */
            if (maze.hasGoal(currentNode)) {
                maze.move(player, currentNode);
                goalReached.set(true);

                return pathFromTo(start, currentNode);
            }
            /**
             * checks if next node is visited or not or if it is the starting node. If not, the player is moved to that
             * node
            */
            if (visited.add(currentNode) || currentNode == startPos) {
                maze.move(player, currentNode);
                boolean firstNeighbour = true;

                /**
                 * looks at every neighbour for the current node. If there is only one unvisited neighbour, the player
                 * will move there, but if there's another or more unvisited neighbour-nodes, a new thread is created
                 * on that node.
                */
                for (int neighbour : maze.neighbors(currentNode)) {

                    if (!visited.contains(neighbour)) {
                        predecessor.put(neighbour, currentNode);

                        if (firstNeighbour) {
                            frontier.push(neighbour);
                            firstNeighbour = false;
                        } else {
                            if (visited.add(neighbour)) {
                                ForkJoinSolver forkThread = new ForkJoinSolver(maze, neighbour, visited, predecessor, goalReached);
                                forks.add(forkThread);
                                forkThread.fork();
                            }
                        }
                    }
                }
            }
        }
        return joinForks();
    }

    //combines fork-threads, adds result of paths and checks whether they found a path or not
    private List<Integer> joinForks() {
        for (ForkJoinSolver fork:forks) {
            List<Integer> result = fork.join();
            if(result != null) {
                return result;
            }
        }
        return null;
    }
}