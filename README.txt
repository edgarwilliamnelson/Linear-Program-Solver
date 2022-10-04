This Linear Program solver was written for CSC445 at the University of Victoria and achieved a grade of 100%. It should be compiled with the provided Makefile by running the command “make” in the directory. The program should be given a linear program to solve via standard input.

E.g “cat cycle.txt | java LPSolver”

This implementation solves a linear program with the following features: 

1. A dictionary based approach is used. 

2. For an initially infeasible dictionary, the auxiliary problem method is used to determine if a feasible point exists.

3. LP infeasibility is detected if the auxiliary problem cannot find a feasible point. 

4. Unboundedness is checked after each iteration of the simplex method. If the LP is feasible and non unbounded, an optimal value will be found.

5. The largest coefficient pivot rule is used in all cases.

6. Cycling is avoided via the lexicographic method, therefore the LP will successfully complete in a finite number of steps on any given LP. 

7. Floating point numbers are used in this solver. Small-value errors are avoided with the “zero()” function that determines if a given value is within a certain threshold of error from zero, the value is replaced with exactly zero if that is the case. Every floating point calculation is checked against the zero function before the value is written. During testing a threshold of 10^(-10) was found to provide correctness for the magnitude of the numbers in the given test cases.
 

Lexicographic Method Description:
To avoid cycling with the largest coefficient pivot rule, the Lexicographic Method is applied. This method adds small epsilon values to each constraint such that e1 > c*e2 for any reasonable real number c in the context of the program. This causes no two constraints to ever be met at equality for the same feasible point.

To implement the Lexicographic Method, the value of the objective function and all variables in the dictionary are represented by an instance of the private “Value” class. 
The Value class fields are a double value, and a double[] array to represent the epsilon values. Each variable being tracked in the dictionary is associated with a value instance in a <String, Value> Map. 

During dictionary initialization each objective variable (E.g “x1”) is associated with an all zero Value. The objective value “zeta” is also associated with an all zero Value. Each constraint is associated with a value containing its assigned double value, and a value of 1.0 in the epsilon array for its row index - 1. 

E.g for a 2x2 LP 
zeta is associated with a value object such as {constant = 0.0, epsilons = [0.0,0.0]}
x1 is associated with a value object such as {constant = 0.0, epsilons = [0.0,0.0]}
x2 is associated with a value object such as {constant = 0.0, epsilons = [0.0,0.0]}
w1 is associated with a value object such as {constant = 0.5, epsilons = [1.0, 0.0]} 
w2 is associated with a value object such as {constant = 3.5, epsilons = [0.0, 1.0]} 

Value objects are compared lexicographically with the ordering <constant, epsilons[0], epsilons[1], …>
When a value object is multiplied by a constant or summed with another value object, the resulting Value is the result of performing the operation component wise on the value object(s).

This implementation of the Lexicographic cycle avoidance technique has been tested on the included files cycle.txt and cycle2.txt. 
Cycle.txt is a problem from the week 5 slides that cycles on the largest coefficient pivot rule, and cycle2.txt is a problem from the text that cycles on the largest coefficient pivot rule.
