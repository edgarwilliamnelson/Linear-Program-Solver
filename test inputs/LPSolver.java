import java.util.*;
import java.math.BigDecimal;
import java.math.MathContext;

public class LPSolver { 

private static ArrayList<ArrayList<Double>> dictionary;
private static boolean infeasible = false;
private static boolean unbounded = false;
private static HashMap<Integer, String> objective_variable_indicies;
private static HashMap<String, Integer> variable_row_indicies;
private static HashMap<Integer, String> index_to_variable_map;
private static HashMap<String, Value> values;
private static HashSet<String> basic_variables;

public static void main (String[] args) {
        dictionary = new ArrayList<ArrayList<Double>>();
        objective_variable_indicies = new HashMap<Integer, String>();
        index_to_variable_map = new HashMap<Integer, String>();
        values = new HashMap<String, Value>();
        basic_variables = new HashSet<>();
        variable_row_indicies = new HashMap<>();

        ArrayList<ArrayList<Double>> program = new ArrayList<ArrayList<Double>>();
        Scanner in = new Scanner(System.in);
        while (in.hasNextLine()){
            String line = in.nextLine();
            Scanner scan = new Scanner(line);
            ArrayList<Double> curr = new ArrayList<>();
            while(scan.hasNextDouble()){
            	Double num = scan.nextDouble();            	
            	curr.add(num);
            }
            if(curr.size() > 0)
              program.add(curr);
        }
        
        generate_dict(program); 
        if(!feasible()){
          solve_auxiliary();
        }
        if(infeasible){
         	System.out.println("infeasible");
         	return;
        }
        simplex();

        if(unbounded){
          System.out.println("unbounded");
         	return;
        }

        System.out.println("optimal");
        String val = values.get("zeta").constant.toString();
        if(val.length() > 9){
          val = val.substring(0, 9);
        }          
        BigDecimal bd = new BigDecimal(values.get("zeta").constant);
        bd = bd.round(new MathContext(7));
        
        System.out.println(bd.doubleValue());
        String xs = "";
        for(int i = 0; i < dictionary.get(0).size(); i++){
           xs = xs + String.format("%g", values.get("x" + (i+1)).constant) + " ";
        }
        System.out.println(xs);
        return;
}

private static void solve_auxiliary(){
    ArrayList<Double> objective_row = dictionary.get(0);
    ArrayList<Double> original_objective_row = new ArrayList<Double>(objective_row);
    HashMap<Integer, String> original_objective_indicies = new HashMap<>(objective_variable_indicies);
    
    //Dictionary is converted to auxiliary problem here, set all objective coefficents to 0 and add -omega to each row.
    for(int i  = 0; i < objective_row.size(); i++){
    	objective_row.set(i, 0.0);
    }
    objective_row.add(-1.0);
    objective_variable_indicies.put(objective_row.size() - 1, "omega");
    values.put("omega", new Value(0.0, dictionary.size() - 1));
    for(int i = 1; i < dictionary.size(); i++){
    	dictionary.get(i).add(1.0);
    }
    
    //Finding the "least feasible constraint" 
    Value min = new Value(Double.MAX_VALUE, dictionary.size() - 1); 
    int minindex = 1;
    for(int i = 1; i < dictionary.size(); i++){
      if(values.get(index_to_variable_map.get(i)).compare(min) < 0) {
        minindex = i;
        min = values.get(index_to_variable_map.get(i));
      }
    }
    
    //Pivot with omega to build feasible auxiliary dict
    pivot(objective_row.size() - 1, minindex);

    //Solve the auxiliary problem
    simplex();

    //If the auxiliary is unbounded, the original is infeasible
    if(unbounded){
      unbounded = false;
      infeasible = true;
    	return;
    }

    //If the zeta value is non zero, original LP is infeasible
    if(!zero(values.get("zeta").constant)){
    	infeasible = true;
    	return;
    }
    
    //Pivot omega into into the objective function to be removed. ***Test is this is needed.
    if(basic_variables.contains("omega")){
      int topivot = -1;
      ArrayList<Double> row = dictionary.get(variable_row_indicies.get("omega"));
      for(int k = 0; k < row.size(); k++){
          if(Math.abs(row.get(k)) > 0.0){
              topivot = k;
              break;
          }
        } 
        if(topivot >= 0){
            pivot(topivot, variable_row_indicies.get("omega"));
        } else {
            dictionary.remove(variable_row_indicies.get("omega"));
        }
    }
            
    //Delete the column of omega
    int omega_index = variable_row_indicies.get("omega");
    values.remove("omega");
    objective_variable_indicies.remove(omega_index);
    variable_row_indicies.remove("omega");
    for(int j = omega_index; j < dictionary.get(0).size() - 1; j++){
        String toMove = objective_variable_indicies.get(j+1);
        objective_variable_indicies.remove(j+1);
        objective_variable_indicies.put(j, toMove);
    }
    for(int k = 0; k < dictionary.size(); k++){
      dictionary.get(k).remove(omega_index);
    }  
    
    //Rebuild the objective row in terms of the new dictionary.
    //Set the row to all zeros
    values.remove("zeta");
    values.put("zeta", new Value(0.0, dictionary.size() - 1));
    ArrayList<Double> new_objective_function = new ArrayList<Double>();
    for(double i : dictionary.get(0)){
    	new_objective_function.add(0.0);
    }
    
    //If a variable from the original objective row is still non basic, just restore it.
    for(int i = 0; i < original_objective_row.size(); i++){
       String variable = original_objective_indicies.get(i);
       if(!basic_variables.contains(variable)){
          if(variable_row_indicies.get(variable) > omega_index)
            new_objective_function.set(variable_row_indicies.get(variable) - 1, original_objective_row.get(i));
          else 
            new_objective_function.set(variable_row_indicies.get(variable), original_objective_row.get(i));
       }
    }

    //Add the partially rebuilt row to the dict
    dictionary.set(0, new_objective_function);

    //If a variable in the original objective row is now basic: Add ((it's row equation) * (it's coefficient in the original objective row)) to the new objective row.
    for(int i = 0; i < original_objective_row.size(); i++){
        String variable = original_objective_indicies.get(i);
        Double coefficent = original_objective_row.get(i);
        if(basic_variables.contains(variable)){
            ArrayList<Double> row = dictionary.get(variable_row_indicies.get(variable));
            Value temp = new Value(values.get(variable));
            temp.timesBy(coefficent);
            values.get("zeta").add(temp);
            for(int k = 0; k < row.size(); k++){
                if(zero(new_objective_function.get(k) + (row.get(k)*coefficent)))
                    new_objective_function.set(k, 0.0);
                else
                    new_objective_function.set(k, new_objective_function.get(k) + (row.get(k)*coefficent));
           }
        } 
    }
}

//Runs the simplex method with greatest coefficient pivot heuristic.
private static void simplex(){
  //Find the greatest coefficient in the objective row
  int pivotIndex = -1;
  double max = 0;
  ArrayList<Double> objectiveFunction = dictionary.get(0);
  for(int i = 0; i < objectiveFunction.size(); i++){
      double curr = objectiveFunction.get(i);
      if(curr > 0) {
       if(curr > max){
          max = objectiveFunction.get(i);
          pivotIndex = i;
         }
         boolean check_unbounded = true;
         for(int j = 1; j < dictionary.size(); j++){
          if(dictionary.get(j).get(i) < 0) {
            check_unbounded = false;
            break;
          }
         }
         if(check_unbounded){
          unbounded = true;
          return;
         }
       }
  }
  
  //No viable pivot case.
  if(pivotIndex == -1){
    return;
  }

  //Find exiting row.
  int constraintIndex = -1;
  Value tighestBound = new Value(Double.MAX_VALUE, dictionary.size() - 1);
  for(int j = 1; j < dictionary.size(); j++){
      Double coefficent = (1.0) / dictionary.get(j).get(pivotIndex);
      if(coefficent < 0) {
          String constraint = index_to_variable_map.get(j);
          Value constraintVal = values.get(constraint);
          Value curr = new Value(constraintVal);
          curr.timesBy(-coefficent);
          if(curr.compare(tighestBound) < 0){
              constraintIndex = j;
              tighestBound = curr;
          }
     }
  }

  //Perform the pivot.
  pivot(pivotIndex, constraintIndex);
  simplex();
}

private static void pivot(int objectiveIndex, int constraintIndex){
    //Grab infromation needed to privot
    String constraint = index_to_variable_map.get(constraintIndex);
    String objective = objective_variable_indicies.get(objectiveIndex);
    Value objectiveValue = values.get(objective);
    Value constraintValue = values.get(constraint);
    Value zeta = values.get("zeta");
    ArrayList<Double> row = dictionary.get(constraintIndex);

    //find the reciprocal of the variable to be solved for in the exiting row.
    Double recip = (1.0) / (dictionary.get(constraintIndex).get(objectiveIndex));

    //Update each coefficient in the exiting row.
    row.set(objectiveIndex, recip);
    for(int i = 0; i < row.size(); i++){
      if(i == objectiveIndex)
        continue;
      row.set(i, row.get(i) * (-recip));
    }    
    constraintValue.timesBy((-recip));

    //updating the objective function and other constraints with the new value of the entering variable.
    for(int i = 0; i < dictionary.size(); i++){
      if(i == constraintIndex){
        continue;
      }

      String otherName = index_to_variable_map.get(i);
      Value otherValue = values.get(otherName);
      ArrayList<Double> other = dictionary.get(i);
      double multiple = other.get(objectiveIndex);
      
      Value temp = new Value(constraintValue);
      temp.timesBy(multiple);
      otherValue.add(temp);

      for(int j = 0; j < other.size(); j++){
        if(j == objectiveIndex){
          if(zero(row.get(j) * multiple))
              other.set(j, 0.0);
          else
              other.set(j, row.get(j) * multiple);
          continue;
        }
        if(zero(other.get(j) + (multiple * row.get(j))))
          other.set(j, 0.0);
        else
          other.set(j, other.get(j) + (multiple * row.get(j)));
      }
    }
    
    //Update all maps tracking dictionary infromation.
    objective_variable_indicies.put(objectiveIndex, constraint);
    index_to_variable_map.put(constraintIndex, objective);
    variable_row_indicies.put(constraint, objectiveIndex);
    variable_row_indicies.put(objective, constraintIndex);
    basic_variables.remove(constraint);
    basic_variables.add(objective);
    values.put(constraint, new Value(0.0, dictionary.size() - 1));
    values.put(objective, constraintValue);
}

private static void generate_dict(ArrayList<ArrayList<Double>> program){
    ArrayList<Double> objectiveFunction = program.get(0);
    for(int i = 0; i < objectiveFunction.size(); i++){
        objective_variable_indicies.put(i, "x" + (i+1));
        values.put("x" + (i+1), new Value(0.0, program.size() - 1));
        variable_row_indicies.put("x" + (i+1), i);
    }

    values.put("zeta", new Value(0.0, program.size()));
    index_to_variable_map.put(0, "zeta");
    variable_row_indicies.put("zeta", 0);
    dictionary.add(objectiveFunction);
    
    for(int i = 1; i < program.size(); i++){
        ArrayList<Double> curr = new ArrayList<>();
        ArrayList<Double> constraint = program.get(i);
        Value v = new Value(constraint.get(constraint.size() - 1), program.size());
        v.addEpsilon(i, 1);
        values.put("w" + i, v);
        
        for(int j = 0; j < constraint.size() - 1; j++){
            Double toAdd = constraint.get(j);
            toAdd = -toAdd;
            curr.add(toAdd);
        }
        dictionary.add(curr);
        index_to_variable_map.put(i, "w" + i);
        basic_variables.add("w" + i);
        variable_row_indicies.put("w" + i, i);
    }
}

private static boolean zero(double d){
  if(Double.compare(Math.abs(d), Math.pow(10, -10)) < 0)
    return true;
  return false;
}

private static boolean feasible(){
    for(int i = 1; i < dictionary.size(); i++){
       String row = index_to_variable_map.get(i);
       Value v = values.get(row);
       if(v.constant < 0)
       	return false;
    }
    return true;
}

private static class Value {
  public Double constant;
  public double[] epsilons;

  public Value(Double constant, int numConstraints){
    this.constant = constant;
    epsilons = new double[numConstraints];
    Arrays.fill(epsilons, 0);
  }

  public Value(Value other){
    this.constant = other.constant;
    this.epsilons = Arrays.copyOf(other.epsilons, other.epsilons.length);
  }

  public void addEpsilon(int index, double val){
    epsilons[index] = val;
  }

  public void timesBy(Double num){
    if(zero(constant*num))
      this.constant = 0.0;
    else
      this.constant = constant*num;

    for(int i = 0; i < epsilons.length; i++){
         if(zero(epsilons[i] * num))
           epsilons[i] = 0.0;
         else
           epsilons[i] = epsilons[i] * num;
    }
  }

  public void add(Value other){
    if(zero(constant + other.constant))
      this.constant = 0.0;
    else
      this.constant = constant + other.constant;

    for(int i = 0; i < epsilons.length; i++){
        if(zero(epsilons[i] + other.epsilons[i]))
            epsilons[i] = 0.0;
        else
            epsilons[i] = epsilons[i] + other.epsilons[i];
    }
  }

  public int compare(Value other){
    if(this.constant != other.constant){
      return Double.compare(this.constant, other.constant);
    }
    for(int i = 0; i < epsilons.length; i++){
      if(this.epsilons[i] != other.epsilons[i]){
        return Double.compare(this.epsilons[i], other.epsilons[i]);
      }
      } 
      return 0;
  }

  public String toString(){
    String out = "CONSTANT IS " + constant + " EPSILONS ARE ";
    for(double e : epsilons){
      out = out + e + " ";
    }
    return out;
  }

  private static boolean zero(double d){
    if(Double.compare(Math.abs(d), Math.pow(10, -10)) < 0)
      return true;
    return false;
  }
}

}
