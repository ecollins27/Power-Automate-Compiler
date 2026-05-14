import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

public class Optimizer {

    public final static Set<String> blockFunctions = new HashSet<>(List.of("Compose", "IncrementVariable", "AppendtoStringVariable", "AppendtoArrayVariable", "to_csv", "to_html", "send_email"));

    public static void replaceDeclaration(SimplifiedASTNode.VariableDeclaration declaration, SimplifiedASTNode.StatementList scope, boolean createReferenceAssignment){
        boolean assignsValue = declaration.defaultValue != null;
        if (assignsValue && createReferenceAssignment){
            SimplifiedASTNode.ReferenceAssignment referenceAssignment = new SimplifiedASTNode.ReferenceAssignment(declaration.variable, declaration.defaultValue);
            if (declaration.prev == null){
                scope.first = referenceAssignment;
                referenceAssignment.prev = null;
            } else {
                declaration.prev.next = referenceAssignment;
                referenceAssignment.prev = declaration.prev;
            }
            referenceAssignment.next = declaration.next;
            if (declaration.next != null){
                declaration.next.prev = referenceAssignment;
            }
        } else {
            if (declaration.prev == null){
                scope.first = declaration.next;
            } else {
                declaration.prev.next = declaration.next;
            }
            if (declaration.next != null){
                declaration.next.prev = declaration.prev;
            }
        }
    }


    public static void findVariableDeclarations(SimplifiedASTNode node, HashMap<String, SimplifiedASTNode.VariableDeclaration> variableDeclarations){
        if (node instanceof SimplifiedASTNode.StatementList){
            SimplifiedASTNode.Statement currentStatement = ((SimplifiedASTNode.StatementList) node).first;
            while (currentStatement != null){
                if (currentStatement instanceof SimplifiedASTNode.VariableDeclaration){
                    boolean setsConstant = true;
                    if (((SimplifiedASTNode.VariableDeclaration) currentStatement).defaultValue != null){
                        for (String variableName: variableDeclarations.keySet()){
                            if (((SimplifiedASTNode.VariableDeclaration) currentStatement).defaultValue.usesVariable(variableName)){
                                setsConstant = false;
                                break;
                            }
                        }
                    }
                    String variableType = ((SimplifiedASTNode.VariableDeclaration) currentStatement).variable.type;
                    String variableName = ((SimplifiedASTNode.VariableDeclaration) currentStatement).variable.name;
                    if (variableDeclarations.containsKey(variableName)){
                        if (!variableDeclarations.get(variableName).variable.type.equals(variableType)){
                            throw new RuntimeException(String.format("Variable %s previously declared with different type", variableName));
                        }
                        replaceDeclaration((SimplifiedASTNode.VariableDeclaration) currentStatement, (SimplifiedASTNode.StatementList) node, true);
                    } else {
                        variableDeclarations.put(variableName, (SimplifiedASTNode.VariableDeclaration) currentStatement);
                        replaceDeclaration((SimplifiedASTNode.VariableDeclaration) currentStatement, (SimplifiedASTNode.StatementList) node, !setsConstant);
                        if (!setsConstant){
                            ((SimplifiedASTNode.VariableDeclaration) currentStatement).defaultValue = null;
                        }
                    }
                } else {
                    findVariableDeclarations(currentStatement, variableDeclarations);
                }
                currentStatement = currentStatement.next;
            }
        } else if (node instanceof SimplifiedASTNode.IfStatement){
            findVariableDeclarations(((SimplifiedASTNode.IfStatement) node).trueBlock, variableDeclarations);
            if (((SimplifiedASTNode.IfStatement) node).falseBlock != null){
                findVariableDeclarations(((SimplifiedASTNode.IfStatement) node).falseBlock, variableDeclarations);
            }
        } else if (node instanceof SimplifiedASTNode.WhileStatement){
            findVariableDeclarations(((SimplifiedASTNode.WhileStatement) node).loopBlock, variableDeclarations);
        } else if (node instanceof SimplifiedASTNode.DoWhileStatement){
            findVariableDeclarations(((SimplifiedASTNode.DoWhileStatement) node).loopBlock, variableDeclarations);
        } else if (node instanceof SimplifiedASTNode.ForEachStatement){
            SimplifiedASTNode.Variable variable = ((SimplifiedASTNode.ForEachStatement) node).variable;
            if (!variableDeclarations.containsKey(variable.name)){
                SimplifiedASTNode.VariableDeclaration variableDeclaration = new SimplifiedASTNode.VariableDeclaration(variable, null);
                variableDeclarations.put(variable.name, variableDeclaration);
            }
            findVariableDeclarations(((SimplifiedASTNode.ForEachStatement) node).loopBlock, variableDeclarations);
        } else if (node instanceof SimplifiedASTNode.TryCatchStatement){
            findVariableDeclarations(((SimplifiedASTNode.TryCatchStatement) node).tryBlock, variableDeclarations);
            if (((SimplifiedASTNode.TryCatchStatement) node).catchBlock != null){
                findVariableDeclarations(((SimplifiedASTNode.TryCatchStatement) node).catchBlock, variableDeclarations);
            }
        }
    }

    public static SimplifiedASTNode.Expression addExpressionPrereqs(SimplifiedASTNode.Expression expression, Stack<SimplifiedASTNode.FunctionCall> prereqs){
        if (expression instanceof SimplifiedASTNode.BoolOperation){
            ((SimplifiedASTNode.BoolOperation) expression).b1 = (SimplifiedASTNode.BoolExpression) addExpressionPrereqs(((SimplifiedASTNode.BoolOperation) expression).b1, prereqs);
            ((SimplifiedASTNode.BoolOperation) expression).b2 = (SimplifiedASTNode.BoolExpression) addExpressionPrereqs(((SimplifiedASTNode.BoolOperation) expression).b2, prereqs);
            return expression;
        } else if (expression instanceof SimplifiedASTNode.NumericOperation){
            ((SimplifiedASTNode.NumericOperation) expression).e1 = addExpressionPrereqs(((SimplifiedASTNode.NumericOperation) expression).e1, prereqs);
            ((SimplifiedASTNode.NumericOperation) expression).e2 = addExpressionPrereqs(((SimplifiedASTNode.NumericOperation) expression).e2, prereqs);
            return expression;
        } else if (expression instanceof SimplifiedASTNode.Reference){
            for (int i = 0; i < ((SimplifiedASTNode.Reference) expression).accessModifiers.size();i++){
                ((SimplifiedASTNode.Reference) expression).accessModifiers.set(i, addExpressionPrereqs(((SimplifiedASTNode.Reference) expression).accessModifiers.get(i), prereqs));
            }
            return expression;
        } else if (expression instanceof SimplifiedASTNode.ObjectConst){
            SimplifiedASTNode.FunctionCall compose = new SimplifiedASTNode.FunctionCall("Compose", expression);
            prereqs.push(compose);
            for (String key: ((SimplifiedASTNode.ObjectConst) expression).value.keySet()){
                ((SimplifiedASTNode.ObjectConst) expression).value.put(key, addExpressionPrereqs(((SimplifiedASTNode.ObjectConst) expression).value.get(key), prereqs));
            }
            return new SimplifiedASTNode.FunctionCall("outputs", compose.blockName);
        } else if (expression instanceof SimplifiedASTNode.FunctionCall){
            if (blockFunctions.contains(((SimplifiedASTNode.FunctionCall) expression).functionName)){
                prereqs.push((SimplifiedASTNode.FunctionCall) expression);
                SimplifiedASTNode.FunctionCall output = new SimplifiedASTNode.FunctionCall("body", ((SimplifiedASTNode.FunctionCall) expression).blockName);
                for (int i = 0; i < ((SimplifiedASTNode.FunctionCall) expression).parameters.size(); i++){
                    ((SimplifiedASTNode.FunctionCall) expression).parameters.set(i, addExpressionPrereqs(((SimplifiedASTNode.FunctionCall) expression).parameters.get(i), prereqs));
                }
                return output;
            } else {
                for (int i = 0; i < ((SimplifiedASTNode.FunctionCall) expression).parameters.size(); i++){
                    ((SimplifiedASTNode.FunctionCall) expression).parameters.set(i, addExpressionPrereqs(((SimplifiedASTNode.FunctionCall) expression).parameters.get(i), prereqs));
                }
                return expression;
            }
        } else {
            return expression;
        }
    }

    public static SimplifiedASTNode.Statement addReferenceAssignmentPrereqs(SimplifiedASTNode.ReferenceAssignment referenceAssignment, Stack<SimplifiedASTNode.FunctionCall> prereqs){
        SimplifiedASTNode.Variable variable = referenceAssignment.variable;
        if ((variable.type.equals("int") || variable.type.equals("string") || variable.type.equals("float")) && !referenceAssignment.accessModifiers.isEmpty()){
            throw new RuntimeException("Variable type cannot have access modifiers");
        }
        if (referenceAssignment.assignedValue.usesVariable(variable.name)){
            if (referenceAssignment.assignedValue instanceof SimplifiedASTNode.NumericOperation && ((SimplifiedASTNode.NumericOperation) referenceAssignment.assignedValue).operator == SimplifiedASTNode.NumericOperator.ADD){
                SimplifiedASTNode.NumericOperation operation = (SimplifiedASTNode.NumericOperation) referenceAssignment.assignedValue;
                SimplifiedASTNode.Expression expression = null;
                if (operation.e1 instanceof SimplifiedASTNode.Reference && ((SimplifiedASTNode.Reference) operation.e1).variable == referenceAssignment.variable){
                    expression = operation.e2;
                } else if (operation.e2 instanceof SimplifiedASTNode.Reference && ((SimplifiedASTNode.Reference) operation.e2).variable == referenceAssignment.variable){
                    expression = operation.e1;
                }
                if (expression != null){
                    SimplifiedASTNode.FunctionCall functionCall = new SimplifiedASTNode.FunctionCall("", referenceAssignment.variable);
                    if (expression.usesVariable(variable.name)){
                        SimplifiedASTNode.FunctionCall compose = new SimplifiedASTNode.FunctionCall("Compose");
                        prereqs.push(compose);
                        compose.parameters.add(addExpressionPrereqs(expression, prereqs));
                    } else {
                        functionCall.parameters.add(addExpressionPrereqs(expression, prereqs));
                    }
                    switch (variable.type){
                        case "int":
                            functionCall.functionName = "IncrementVariable";
                            break;
                        case "float":
                            functionCall.functionName = "IncrementVariable";
                            break;
                        case "string":
                            functionCall.functionName = "AppendtoStringVariable";
                            break;
                        case "array":
                            functionCall.functionName = "AppendToArrayVariable";
                            break;
                        default:
                            throw new RuntimeException("Cannot add boolean or object types");
                    }
                    return functionCall;
                }
            } else if (referenceAssignment.assignedValue instanceof SimplifiedASTNode.NumericOperation && ((SimplifiedASTNode.NumericOperation) referenceAssignment.assignedValue).operator == SimplifiedASTNode.NumericOperator.SUBTRACT){
                SimplifiedASTNode.NumericOperation operation = (SimplifiedASTNode.NumericOperation) referenceAssignment.assignedValue;
                SimplifiedASTNode.Expression expression = null;
                if (operation.e1 instanceof SimplifiedASTNode.Reference && ((SimplifiedASTNode.Reference) operation.e1).variable == referenceAssignment.variable){
                    expression = operation.e2;
                }
                if (expression != null){
                    SimplifiedASTNode.FunctionCall functionCall = new SimplifiedASTNode.FunctionCall("", referenceAssignment.variable);
                    if (expression.usesVariable(variable.name)){
                        SimplifiedASTNode.FunctionCall compose = new SimplifiedASTNode.FunctionCall("Compose");
                        prereqs.push(compose);
                        compose.parameters.add(addExpressionPrereqs(expression, prereqs));
                    } else {
                        functionCall.parameters.add(addExpressionPrereqs(expression, prereqs));
                    }
                    switch (variable.type){
                        case "int":
                            functionCall.functionName = "DecrementVariable";
                            break;
                        case "float":
                            functionCall.functionName = "DecrementVariable";
                            break;
                        default:
                            throw new RuntimeException("Cannot subtract boolean, string, array, or object types");
                    }
                    return functionCall;
                }
            }
        } else {
            return null;
        }
        SimplifiedASTNode.FunctionCall compose = new SimplifiedASTNode.FunctionCall("Compose");
        prereqs.push(compose);
        compose.parameters.add(addExpressionPrereqs(referenceAssignment.assignedValue, prereqs));
        return new SimplifiedASTNode.FunctionCall("outputs", compose.blockName);
    }

    public static Stack<SimplifiedASTNode.FunctionCall> getStatementPrereqs(SimplifiedASTNode.Statement statement, SimplifiedASTNode.StatementList scope){
        Stack<SimplifiedASTNode.FunctionCall> prereqs = new Stack<>();
        if (statement instanceof SimplifiedASTNode.VariableDeclaration){
            if (((SimplifiedASTNode.VariableDeclaration) statement).defaultValue != null){
                ((SimplifiedASTNode.VariableDeclaration) statement).defaultValue = addExpressionPrereqs(((SimplifiedASTNode.VariableDeclaration) statement).defaultValue, prereqs);
            }
            return prereqs;
        } else if (statement instanceof SimplifiedASTNode.ReferenceAssignment){
            SimplifiedASTNode.Statement replacement = addReferenceAssignmentPrereqs((SimplifiedASTNode.ReferenceAssignment) statement, prereqs);
            if (replacement != null){
                replacement.prev = statement.prev;
                replacement.next = statement.next;
                if (statement.prev != null){
                    statement.prev.next = replacement;
                } else {
                    scope.first = replacement;
                } if (statement.next != null){
                    statement.next.prev = replacement;
                }
            }
            return prereqs;
        } else if (statement instanceof SimplifiedASTNode.IfStatement){
            ((SimplifiedASTNode.IfStatement) statement).condition = (SimplifiedASTNode.BoolExpression) addExpressionPrereqs(((SimplifiedASTNode.IfStatement) statement).condition, prereqs);
            addStatementPrereqs(((SimplifiedASTNode.IfStatement) statement).trueBlock);
            if (((SimplifiedASTNode.IfStatement) statement).falseBlock != null){
                addStatementPrereqs(((SimplifiedASTNode.IfStatement) statement).falseBlock);
            }
            return prereqs;
        } else if (statement instanceof SimplifiedASTNode.WhileStatement){
            ((SimplifiedASTNode.WhileStatement) statement).condition = (SimplifiedASTNode.BoolExpression) addExpressionPrereqs(((SimplifiedASTNode.WhileStatement) statement).condition, prereqs);
            addStatementPrereqs(((SimplifiedASTNode.WhileStatement) statement).loopBlock);
            return prereqs;
        } else if (statement instanceof SimplifiedASTNode.DoWhileStatement){
            ((SimplifiedASTNode.DoWhileStatement) statement).condition = (SimplifiedASTNode.BoolExpression) addExpressionPrereqs(((SimplifiedASTNode.DoWhileStatement) statement).condition, prereqs);
            addStatementPrereqs(((SimplifiedASTNode.DoWhileStatement) statement).loopBlock);
            return prereqs;
        } else if (statement instanceof SimplifiedASTNode.ForEachStatement){
            SimplifiedASTNode.FunctionCall items = new SimplifiedASTNode.FunctionCall("items", statement.blockName);
            SimplifiedASTNode.ReferenceAssignment referenceAssignment = new SimplifiedASTNode.ReferenceAssignment(((SimplifiedASTNode.ForEachStatement) statement).variable, items);
            SimplifiedASTNode.StatementList loopBlock = ((SimplifiedASTNode.ForEachStatement) statement).loopBlock;
            referenceAssignment.prev = null;
            referenceAssignment.next = loopBlock.first;
            if (loopBlock.first != null){
                loopBlock.first.prev = referenceAssignment;
            }
            loopBlock.first = referenceAssignment;
            ((SimplifiedASTNode.ForEachStatement) statement).expression = addExpressionPrereqs(((SimplifiedASTNode.ForEachStatement) statement).expression, prereqs);
            addStatementPrereqs(loopBlock);
            return prereqs;
        } else if (statement instanceof SimplifiedASTNode.TryCatchStatement){
            addStatementPrereqs(((SimplifiedASTNode.TryCatchStatement) statement).tryBlock);
            if (((SimplifiedASTNode.TryCatchStatement) statement).catchBlock != null){
                addStatementPrereqs(((SimplifiedASTNode.TryCatchStatement) statement).catchBlock);
            }
            return prereqs;
        } else if (statement instanceof SimplifiedASTNode.FunctionCall){
            SimplifiedASTNode.FunctionCall functionCall = (SimplifiedASTNode.FunctionCall) statement;
            for (int i = 0; i < functionCall.parameters.size();i++){
                functionCall.parameters.set(i, addExpressionPrereqs(functionCall.parameters.get(i), prereqs));
            }
            return prereqs;
        } else {
            throw new RuntimeException(String.format("Unknown statement %s", statement.toString()));
        }
    }

    public static void addStatement(SimplifiedASTNode.FunctionCall function, SimplifiedASTNode.Statement currentState, SimplifiedASTNode.StatementList scope){
        if (currentState.prev == null){
            scope.first = function;
        } else {
            currentState.prev.next = function;
        }
        function.prev = currentState.prev;
        currentState.prev = function;
        function.next = currentState;
    }

    public static void addStatementPrereqs(SimplifiedASTNode.StatementList statementList){
        SimplifiedASTNode.Statement currentStatement = statementList.first;
        while (currentStatement != null){
            Stack<SimplifiedASTNode.FunctionCall> prereqs = getStatementPrereqs(currentStatement, statementList);
            while (!prereqs.isEmpty()){
                addStatement(prereqs.pop(), currentStatement, statementList);
            }
            currentStatement = currentStatement.next;
        }
    }

    public static void reorderAST(SimplifiedASTNode.StatementList rootNode){
        HashMap<String, SimplifiedASTNode.VariableDeclaration> variableDeclarations = new HashMap<>();
        findVariableDeclarations(rootNode, variableDeclarations);
        if (!variableDeclarations.isEmpty()){
            int numDeclarations = variableDeclarations.size();
            ArrayList<SimplifiedASTNode.VariableDeclaration> declarationList = new ArrayList<>(variableDeclarations.values());
            for (int i = 0; i < numDeclarations - 1; i++){
                declarationList.get(i).next = declarationList.get(i + 1);
                declarationList.get(numDeclarations - 1 - i).prev = declarationList.get(numDeclarations - 2 - i);
            }
            declarationList.get(0).prev = null;
            declarationList.get(numDeclarations - 1).next = rootNode.first;
            rootNode.first.prev = declarationList.get(numDeclarations - 1);
            rootNode.first = declarationList.get(0);
        }
        addStatementPrereqs(rootNode);
    }
}
