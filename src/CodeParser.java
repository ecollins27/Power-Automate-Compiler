import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class CodeParser {

    HashMap<String, String[][]> productions;
    HashMap<String, boolean[]> nullable;
    HashMap<String, Set<String>> first;
    HashMap<String, Set<String>> follow;

    public CodeParser(String filename) throws FileNotFoundException {
        File file = new File(filename);
        Scanner grammar = new Scanner(file);
        productions = new HashMap<>();
        while (grammar.hasNextLine()){
            String line = grammar.nextLine();
            if (line.isEmpty()){
                continue;
            }
            String productionName = line.split("->")[0];
            line = line.split("->")[1];
            String[] productionStrings = line.split("\\|");
            productions.put(productionName, new String[productionStrings.length][0]);
            for (int i = 0; i < productionStrings.length;i++){
                productions.get(productionName)[i] = productionStrings[i].split(" ");
            }
        }
        first = new HashMap<>();
        follow = new HashMap<>();
        nullable = new HashMap<>();
        propagateFirst();
        propagateFollow();
        propagateNullable();
    }

    private void propagateNullable(){
        for (String key: productions.keySet()){
            for (String[] production: productions.get(key)){
                for (String element: production){
                    propagateNullable(element);
                }
            }
        }
    }

    private boolean propagateNullable(String productionName){
        if (productionName.equals("~epsilon")){
            return true;
        }
        if (!productions.containsKey(productionName)){
            return false;
        }
        if (nullable.containsKey(productionName)) {
            for (boolean n: nullable.get(productionName)){
                if (n){
                    return true;
                }
            }
            return false;
        }
        String[][] prodOptions = productions.get(productionName);
        nullable.put(productionName, new boolean[prodOptions.length]);
        boolean isNullable = false;
        for (int i = 0; i < prodOptions.length; i++){
            boolean prodNullable = true;
            for (int j = 0; j < prodOptions[i].length; j++){
                prodNullable = prodNullable && propagateNullable(prodOptions[i][j]);
            }
            isNullable = isNullable || prodNullable;
            nullable.get(productionName)[i] = prodNullable;
        }
        return isNullable;
    }

    private void propagateFirst(String productionName){
        if (!productions.containsKey(productionName)) {
            first.put(productionName, new HashSet<>(List.of(productionName)));
            return;
        } if (!first.containsKey(productionName)){
            first.put(productionName, new HashSet<>());
        }
        for (String[] production: productions.get(productionName)){
            if (production[0].charAt(0) != '~'){
                first.get(productionName).add(production[0]);
                continue;
            }
            if (!first.containsKey(production[0])){
                propagateFirst(production[0]);
            }
            first.get(productionName).addAll(first.get(production[0]));
        }
    }

    private void propagateFirst(){
        for (String key: productions.keySet()){
            for (String[] production: productions.get(key)){
                for (String element: production){
                    propagateFirst(element);
                }
            }
        }
    }

    private void propagateFollow(){
        for (String key: productions.keySet()){
            for (String[] production: productions.get(key)){
                for (String element: production){
                    follow.put(element, new HashSet<>());
                }
            }
        }
        for (String key: productions.keySet()){
            for (String[] production: productions.get(key)){
                if (production.length > 1 && production[0].charAt(0) == '~'){
                    if (production[1].charAt(0) == '~'){
                        follow.get(production[0]).addAll(first.get(production[1]));
                    } else {
                        follow.get(production[0]).add(production[1]);
                    }
                }
            }
        }
    }

    private boolean contains(Set<String> tokenSet, Token token){
        for (String str: tokenSet){
            if (token.tokenType().equals("const") && str.equals(token.token())){
                return true;
            } else if (!token.tokenType().equals("const") && str.equals(token.tokenType())){
                return true;
            }
        }
        return false;
    }

    private String[] pickProduction(String prodName, Token nextToken){
        Token epsilon = new Token("", "~epsilon");
        String[][] prodOptions = productions.get(prodName);
        if (prodOptions.length == 1){
            return prodOptions[0];
        }
        int productionIndex = -1;
        for (int i = 0; i < prodOptions.length; i++){
            if (contains(first.get(prodOptions[i][0]), nextToken)){
                if (productionIndex != -1){
                    throw new RuntimeException("Ambiguous productions " + productionIndex + "," + i + " for type " + prodName + " with next token " + nextToken.toString());
                }
                productionIndex = i;
            } else if (prodOptions[i].length > 1 && contains(first.get(prodOptions[i][0]), epsilon) && contains(first.get(prodOptions[i][1]), nextToken)){
                if (productionIndex != -1){
                    throw new RuntimeException("Ambiguous productions " + productionIndex + "," + i + " for type " + prodName + " with next token " + nextToken.toString());
                }
                productionIndex = i;
            }
        }
        if (productionIndex == -1){
            boolean[] prodNullable = nullable.get(prodName);
            for (int i = 0; i < prodNullable.length; i++){
                if (prodNullable[i]){
                    return prodOptions[i];
                }
            }
            throw new RuntimeException("No valid productions found for " + prodName + " and next token " + nextToken.toString());
        }
        return prodOptions[productionIndex];
    }

    public SimplifiedASTNode.StatementList toAST(CodeScanner scanner){
        Stack<ASTNode> stack = new Stack<>();
        ASTNode programNode = new ASTNode("~program");
        stack.push(programNode);
        Token nextToken = scanner.getNextToken();
        while (!stack.isEmpty()){
            ASTNode popElement = stack.pop();
            if (popElement.token.tokenType().equals("~epsilon")){
                continue;
            }
            if (nextToken == null){
                nextToken = new Token("", "~epsilon");
            }
            String popType = popElement.token.tokenType();
            if (!productions.containsKey(popType)){
                if (popType.equals("const")){
                    if (!nextToken.tokenType().equals("const") || !nextToken.token().equals(popElement.token.token())){
                        throw new RuntimeException("Expected constant token \"" + popElement.token.token() + "\".  Found \"" + nextToken.token() + "\" with type " + nextToken.tokenType());
                    }

                } else {
                    if (!nextToken.tokenType().equals(popType)){
                        throw new RuntimeException("Expected token of type " + popType + ".  Found token of type " + nextToken.tokenType() + ".");
                    }
                    popElement.token = nextToken;
                }
                nextToken = scanner.getNextToken();
            } else {
                String[] chosenProduction = pickProduction(popType, nextToken);
                ASTNode[] children = popElement.setProduction(chosenProduction);
                for (int i = children.length - 1; i >= 0; i--){
                    stack.push(children[i]);
                }
            }
        }
        programNode.trim();
        return optimizeAST(programNode);
    }

    private SimplifiedASTNode.BoolExpression optimizeBoolExpression(ASTNode node, HashMap<String, SimplifiedASTNode.Variable> variables){
        String nodeType = node.token.tokenType();
        if (nodeType.equals("~epsilon")){
            return null;
        } else if (nodeType.equals("~bool_expression") || nodeType.equals("~or_expression_tail_opt")){
            if (node.children[1].token.tokenType().equals("~epsilon")){
                return optimizeBoolExpression(node.children[0], variables);
            }
            return new SimplifiedASTNode.BoolOperation(optimizeBoolExpression(node.children[0], variables), SimplifiedASTNode.BoolOperator.OR, optimizeBoolExpression(node.children[1], variables));
        } else if (nodeType.equals("~and_expression") || nodeType.equals("~and_expression_tail_opt")){
            if (node.children[1].token.tokenType().equals("~epsilon")){
                return optimizeBoolExpression(node.children[0], variables);
            }
            return new SimplifiedASTNode.BoolOperation(optimizeBoolExpression(node.children[0], variables), SimplifiedASTNode.BoolOperator.AND, optimizeBoolExpression(node.children[1], variables));
        } else if (nodeType.equals("~bool_value")){
            if (node.children[0].token.tokenType().equals("~bool_value")){
                return new SimplifiedASTNode.BoolOperation(optimizeBoolExpression(node.children[0], variables), SimplifiedASTNode.BoolOperator.NOT, null);
            } else if (node.children.length > 1){
                SimplifiedASTNode.RelOperator operator;
                switch (node.children[1].token.token()){
                    case "<":
                        operator = SimplifiedASTNode.RelOperator.LESS_THAN;
                        break;
                    case "<=":
                        operator = SimplifiedASTNode.RelOperator.LESS_THAN_EQUALS;
                        break;
                    case ">":
                        operator = SimplifiedASTNode.RelOperator.GREATER_THAN;
                        break;
                    case ">=":
                        operator = SimplifiedASTNode.RelOperator.GREATER_THAN_EQUALS;
                        break;
                    case "==":
                        operator = SimplifiedASTNode.RelOperator.EQUALS;
                        break;
                    case "!=":
                        operator = SimplifiedASTNode.RelOperator.NOT_EQUALS;
                        break;
                    default:
                        throw new RuntimeException(String.format("Unknown relation operator %s", node.children[1].token.token()));
                }
                return new SimplifiedASTNode.ExpressionComparison(optimizeExpression(node.children[0], variables), operator, optimizeExpression(node.children[2], variables));
            }
            return optimizeBoolExpression(node.children[0], variables);
        } else if (nodeType.equals("~bool_const")){
            return new SimplifiedASTNode.BoolConst(Boolean.parseBoolean(node.token.token()));
        } else {
            throw new RuntimeException(String.format("Unknown boolean expression type %s", nodeType));
        }
    }

    private SimplifiedASTNode.Expression optimizeExpression(ASTNode node, HashMap<String, SimplifiedASTNode.Variable> variables) {
        String nodeType = node.token.tokenType();
        if (nodeType.equals("~epsilon")) {
            return null;
        } else if (nodeType.equals("~expression")) {
            if (node.children[1].token.tokenType().equals("~epsilon")) {
                return optimizeExpression(node.children[0], variables);
            }
            SimplifiedASTNode.NumericOperator op;
            String operator = node.children[1].children[0].token.token();
            if (operator.equals("+")) {
                op = SimplifiedASTNode.NumericOperator.ADD;
            } else {
                op = SimplifiedASTNode.NumericOperator.SUBTRACT;
            }
            return new SimplifiedASTNode.NumericOperation(optimizeExpression(node.children[0], variables), op, optimizeExpression(node.children[1], variables));
        } else if (nodeType.equals("~add_expression_tail_opt")) {
            if (node.children[2].token.tokenType().equals("~epsilon")) {
                return optimizeExpression(node.children[1], variables);
            }
            SimplifiedASTNode.NumericOperator op;
            String operator = node.children[1].children[0].token.token();
            if (operator.equals("+")) {
                op = SimplifiedASTNode.NumericOperator.ADD;
            } else {
                op = SimplifiedASTNode.NumericOperator.SUBTRACT;
            }
            return new SimplifiedASTNode.NumericOperation(optimizeExpression(node.children[0], variables), op, optimizeExpression(node.children[1], variables));
        } else if (nodeType.equals("~mult_expression")) {
            if (node.children[1].token.tokenType().equals("~epsilon")) {
                return optimizeExpression(node.children[0], variables);
            }
            SimplifiedASTNode.NumericOperator op;
            String operator = node.children[1].children[0].token.token();
            if (operator.equals("*")) {
                op = SimplifiedASTNode.NumericOperator.MULTIPLY;
            } else {
                op = SimplifiedASTNode.NumericOperator.DIVIDE;
            }
            return new SimplifiedASTNode.NumericOperation(optimizeExpression(node.children[0], variables), op, optimizeExpression(node.children[1], variables));
        } else if (nodeType.equals("~mult_expression_tail_opt")){
            if (node.children[2].token.tokenType().equals("~epsilon")) {
                return optimizeExpression(node.children[1], variables);
            }
            SimplifiedASTNode.NumericOperator op;
            String operator = node.children[1].children[0].token.token();
            if (operator.equals("*")) {
                op = SimplifiedASTNode.NumericOperator.MULTIPLY;
            } else {
                op = SimplifiedASTNode.NumericOperator.DIVIDE;
            }
            return new SimplifiedASTNode.NumericOperation(optimizeExpression(node.children[0], variables), op, optimizeExpression(node.children[1], variables));
        } else if (nodeType.equals("~value") || nodeType.equals("~default_value_opt")){
            return optimizeExpression(node.children[0], variables);
        } else if (nodeType.equals("~ref")){
            SimplifiedASTNode.Reference reference = new SimplifiedASTNode.Reference(variables.get(node.children[0].token.token()));
            ASTNode accessModifier = node.children[1];
            while (!accessModifier.token.tokenType().equals("~epsilon")){
                reference.accessModifiers.add(optimizeExpression(accessModifier.children[0], variables));
                accessModifier = accessModifier.children[1];
            }
            return reference;
        } else if (nodeType.equals("~int_const")){
            return new SimplifiedASTNode.IntConst(Integer.parseInt(node.token.token()));
        } else if (nodeType.equals("~float_const")){
            return new SimplifiedASTNode.FloatConst(Double.parseDouble(node.token.token()));
        } else if (nodeType.equals("~string_const")){
            return new SimplifiedASTNode.StringConst(node.token.token().substring(1, node.token.token().length() - 1));
        } else if (nodeType.equals("~bool_const")){
            return new SimplifiedASTNode.BoolConst(Boolean.parseBoolean(node.token.token()));
        } else if (nodeType.equals("~array_const")){
            SimplifiedASTNode.ArrayConst arrayConst = new SimplifiedASTNode.ArrayConst();
            ASTNode expressionList = node.children[0];
            while (!expressionList.token.tokenType().equals("~epsilon")){
                arrayConst.value.add(optimizeExpression(expressionList.children[0], variables));
                expressionList = expressionList.children[1];
            }
            return arrayConst;
        } else if (nodeType.equals("~object_const")){
            SimplifiedASTNode.ObjectConst objectConst = new SimplifiedASTNode.ObjectConst();
            ASTNode mapList = node.children[0];
            while (!mapList.token.tokenType().equals("~epsilon")){
                objectConst.value.put(mapList.children[0].token.token(), optimizeExpression(mapList.children[1], variables));
                mapList = mapList.children[2];
            }
            return objectConst;
        } else if (nodeType.equals("~bool_expression")){
            return optimizeBoolExpression(node, variables);
        } else if (nodeType.equals("~function_call")){
            SimplifiedASTNode.FunctionCall functionCall = new SimplifiedASTNode.FunctionCall(node.children[0].token.token());
            ASTNode parameterList = node.children[1];
            while (!parameterList.token.tokenType().equals("~epsilon")){
                functionCall.parameters.add(optimizeExpression(parameterList.children[0], variables));
                parameterList = parameterList.children[1];
            }
            return functionCall;
        } else {
            throw new RuntimeException(String.format("Unknown expression type %s", nodeType));
        }
    }

    private SimplifiedASTNode.Statement optimizeStatement(ASTNode node, HashMap<String, SimplifiedASTNode.Variable> variables){
        String nodeType = node.token.tokenType();
        if (nodeType.equals("~epsilon")){
            return null;
        } else if (nodeType.equals("~statement")){
            return optimizeStatement(node.children[0], variables);
        } else if (nodeType.equals("~variable_declaration")){
            if (variables.containsKey(node.children[1].token.token())){
                throw new RuntimeException(String.format("String with name \"%s\" already exists", node.children[1].token.token()));
            }
            SimplifiedASTNode.Variable variable = new SimplifiedASTNode.Variable(node.children[0].token.token(), node.children[1].token.token());
            variables.put(variable.name, variable);
            return new SimplifiedASTNode.VariableDeclaration(variable, optimizeExpression(node.children[2], variables));
        } else if (nodeType.equals("~reference_assignment")){
            SimplifiedASTNode.ReferenceAssignment referenceAssignment = new SimplifiedASTNode.ReferenceAssignment(variables.get(node.children[0].children[0].token.token()),optimizeExpression(node.children[1], variables));
            ASTNode accessModifier = node.children[0].children[1];
            while (!accessModifier.token.tokenType().equals("~epsilon")){
                referenceAssignment.accessModifiers.add(optimizeExpression(accessModifier.children[0], variables));
                accessModifier = accessModifier.children[1];
            }
            return referenceAssignment;
        } else if (nodeType.equals("~if_statement")){
            return new SimplifiedASTNode.IfStatement(optimizeBoolExpression(node.children[0], variables), optimizeStatementList(node.children[1], variables), optimizeStatementList(node.children[2], variables));
        } else if (nodeType.equals("~while_statement")){
            return new SimplifiedASTNode.WhileStatement(optimizeBoolExpression(node.children[0], variables), optimizeStatementList(node.children[1], variables));
        } else if (nodeType.equals("~do_while_statement")){
            return new SimplifiedASTNode.DoWhileStatement(optimizeBoolExpression(node.children[1], variables), optimizeStatementList(node.children[0], variables));
        } else if (nodeType.equals("~for_each_statement")){
            String variableType = node.children[0].token.token();
            String variableName = node.children[1].token.token();
            SimplifiedASTNode.Variable variable;
            if (variables.containsKey(variableName)){
                if (!variables.get(variableName).type.equals(variableType)){
                    throw new RuntimeException(String.format("Variable with name %s already declared with type %s", variableName, variableType));
                }
                variable = variables.get(variableName);
            } else {
                variable = new SimplifiedASTNode.Variable(variableType, variableName);
                variables.put(variableName, variable);
            }
            return new SimplifiedASTNode.ForEachStatement(variable, optimizeExpression(node.children[2], variables), optimizeStatementList(node.children[3], variables));
        }
        else if (nodeType.equals("~try_catch_statement")){
            return new SimplifiedASTNode.TryCatchStatement(optimizeStatementList(node.children[0], variables), optimizeStatementList(node.children[1], variables));
        }
        else if (nodeType.equals("~function_call")){
            SimplifiedASTNode.FunctionCall functionCall = new SimplifiedASTNode.FunctionCall(node.children[0].token.token());
            ASTNode parameterList = node.children[1];
            while (!parameterList.token.tokenType().equals("~epsilon")){
                functionCall.parameters.add(optimizeExpression(parameterList.children[0], variables));
                parameterList = parameterList.children[1];
            }
            return functionCall;
        } else {
            throw new RuntimeException(String.format("Unknown statement type %s", nodeType));
        }
    }

    private SimplifiedASTNode.StatementList optimizeStatementList(ASTNode node, HashMap<String, SimplifiedASTNode.Variable> variables){
        String nodeType = node.token.tokenType();
        if (nodeType.equals("~epsilon")){
            return null;
        }
        if (nodeType.equals("~else_opt") || nodeType.equals("~catch_opt")){
            return optimizeStatementList(node.children[0], variables);
        }
        SimplifiedASTNode.StatementList statementList = new SimplifiedASTNode.StatementList();
        statementList.first = optimizeStatement(node.children[0], variables);
        SimplifiedASTNode.Statement lastStatement = statementList.first;
        lastStatement.prev = null;
        ASTNode statementListNode = node.children[1];
        while (!statementListNode.token.tokenType().equals("~epsilon")){
            lastStatement.next = optimizeStatement(statementListNode.children[0], variables);
            lastStatement.next.prev = lastStatement;
            lastStatement = lastStatement.next;
            statementListNode = statementListNode.children[1];
        }
        return statementList;
    }

    private SimplifiedASTNode.StatementList optimizeAST(ASTNode node){
        HashMap<String, SimplifiedASTNode.Variable> variables = new HashMap<>();
        return optimizeStatementList(node.children[0], variables);
    }
}
