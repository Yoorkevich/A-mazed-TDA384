package amazed.solver;

import amazed.maze.Maze;

import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private ConcurrentSkipListSet visited = new ConcurrentSkipListSet();
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

    public ForkJoinSolver(Maze maze, int forkAfter, int startPos, ConcurrentSkipListSet visited, ArrayList<ForkJoinSolver> forks, Map<Integer, Integer> predecessor) {
        this(maze);
        this.forkAfter = forkAfter;
        this.startPos = startPos;
        this.visited = visited;
        this.forks = forks;
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
        int nSteps = 0;
        int currentNode;


        while (!frontier.empty() && !goalReached.get()) {
            currentNode = frontier.pop();

            if (maze.hasGoal(currentNode)) {
                maze.move(player, currentNode);
                nSteps++;
                goalReached.set(true);
                System.out.println(pathFromTo(start, currentNode));
                return pathFromTo(start, currentNode);
            }
            if (visited.add(currentNode) || currentNode == startPos) {
                maze.move(player, currentNode);
                nSteps++;
                boolean firstNeighbour = true;

                for (int neighbour : maze.neighbors(currentNode)) {

                    if (!visited.contains(neighbour)) {
                        predecessor.put(neighbour, currentNode);
                        frontier.push(neighbour);

                        if (maze.neighbors(currentNode).size() > 2) {
                            if (firstNeighbour) {

                                firstNeighbour = false;

                            } else {
                                if (visited.add(neighbour)) {
                                    ForkJoinSolver forkThread = new ForkJoinSolver(maze, nSteps, neighbour, visited, forks, predecessor, goalReached);
                                    forks.add(forkThread);
                                    forkThread.fork();
                                }
                            }
                        }
                    }
                }
            }
        }
        for (ForkJoinSolver fork : forks) {
            fork.join();
        }
        return null;
    }
    private List<Integer> joinForks() {
        for (ForkJoinSolver solver:forks) {
            List<Integer> result = solver.join();
            if(result!=null) {
                List<Integer> myPath = pathFromTo(start, predecessor.get(solver.startPos));
                myPath.addAll(result);
                return myPath;
            }
        }
        return null;
    }
}