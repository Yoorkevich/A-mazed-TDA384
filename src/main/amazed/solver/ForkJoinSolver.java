package amazed.solver;

import amazed.maze.Maze;

import java.util.ArrayList;
import java.util.List;
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
    private static AtomicBoolean goalReached = new AtomicBoolean();
    private List<ForkJoinSolver> forks = new ArrayList<ForkJoinSolver>();
    private ConcurrentSkipListSet visited = new ConcurrentSkipListSet();
    private int startPos;

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

    public ForkJoinSolver(Maze maze, int forkAfter, int startPos) {
        this(maze);
        this.forkAfter = forkAfter;
        this.startPos = startPos;
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

    //{}
    private List<Integer> parallelSearch() {
        if(forks.size() == 0){
            startPos = start;
        }
        int player = maze.newPlayer(startPos);
        frontier.push(startPos);
        int nSteps = 0;

        while (!frontier.empty() && !goalReached.get()) {

            int currentNode = frontier.pop();

            if (maze.hasGoal(currentNode)) {
                maze.move(player, currentNode);
                nSteps++;
                goalReached.set(true);
                for (ForkJoinSolver thread : forks) {
                    thread.join();
                }
                return pathFromTo(start, currentNode);
            }
            if (visited.add(currentNode)) {
                maze.move(player, currentNode);
                nSteps++;
                boolean firstNeighbour = true;
                for (int neighbour : maze.neighbors(currentNode)) {
                    predecessor.put(neighbour, currentNode);

                    if (!visited.contains(neighbour)) {
                        frontier.push(neighbour);

                        if (firstNeighbour) {
                            firstNeighbour = false;
                        } else {
                                ForkJoinSolver forkThread = new ForkJoinSolver(maze, nSteps, currentNode);
                                forks.add(forkThread);
                                forkThread.fork();
                        }
                    }
                }
            }
        }
        for (ForkJoinSolver thread : forks) {
            thread.join();
        }
        return null;
    }
}