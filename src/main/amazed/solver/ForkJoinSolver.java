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
    private ArrayList<ForkJoinSolver> forks = new ArrayList<>();
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

    public ForkJoinSolver(
            Maze maze, int startPos, Set<Integer> visited, Map<Integer, Integer> predecessor, AtomicBoolean goalReached) {
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
        frontier.push(startPos);

        int currentNode;

        //if no nodes are to be visited and no fork has found goal,
        while (!frontier.empty() && !goalReached.get()) {
            currentNode = frontier.pop();

            if (maze.hasGoal(currentNode)) {
                maze.move(player, currentNode);
                goalReached.set(true);
                return pathFromTo(start, currentNode);
            }
            //checks if next node is visited or not or if it is the starting node
            if (visited.add(currentNode) || currentNode == startPos) {
                maze.move(player, currentNode);
                boolean firstNeighbour = true;

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
            if(result!=null) {
                return result;
            }
        }
        return null;
    }
}