import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public abstract class SimplifiedASTNode {

    public static class StatementList extends SimplifiedASTNode {
        Statement first;

        public String toString(){
            String toString = "";
            Statement currentStatement = first;
            while (currentStatement != null){
                toString += currentStatement.toString() + "\n";
                currentStatement = currentStatement.next;
            }
            return toString;
        }
    }

    public static abstract class Statement extends SimplifiedASTNode {
        Statement next;
        Statement prev;

        StringConst blockName;

        public Statement(){
            blockName = new StringConst(this.getClass().toString());
        }
    }

    public static class VariableDeclaration extends Statement {
        Variable variable;
        Expression defaultValue;

        public VariableDeclaration(String type, String value, Expression defaultValue){
            variable = new Variable(type, value);
            this.defaultValue = defaultValue;
        }

        public VariableDeclaration(Variable variable, Expression defaultValue){
            this.variable = variable;
            this.defaultValue = defaultValue;
        }

        public String toString(){
            if (defaultValue != null){
                return String.format("%s %s = %s", variable.type, variable.name, defaultValue.toString());
            }
            return String.format("%s %s", variable.type, variable.name);
        }
    }

    public static class ReferenceAssignment extends Statement {
        Variable variable;
        ArrayList<Expression> accessModifiers;

        Expression assignedValue;

        public ReferenceAssignment(Variable variable, Expression assignedValue, Expression... accessModifiers){
            this.variable = variable;
            this.assignedValue = assignedValue;
            this.accessModifiers = new ArrayList<>(Arrays.asList(accessModifiers));
        }

        public String toString(){
            String toString = variable.name;
            if (!accessModifiers.isEmpty()){
                toString += "[";
                for (Expression e: accessModifiers){
                    toString += e.toString() + ", ";
                }
                toString += "]";
            }
            toString += " = " + assignedValue.toString();
            return toString;
        }
    }

    public static class IfStatement extends Statement {
        BoolExpression condition;
        StatementList trueBlock;
        StatementList falseBlock;


        public IfStatement(BoolExpression condition, StatementList trueBlock, StatementList falseBlock){
            this.condition = condition;
            this.trueBlock = trueBlock;
            this.falseBlock = falseBlock;
        }

        public String toString(){
            String toString = String.format("if (%s) {\n%s\n}", condition.toString(), trueBlock.toString());
            if (falseBlock != null){
                toString += String.format(" else {\n%s\n}", falseBlock.toString());
            }
            return toString;
        }
    }

    public static class WhileStatement extends Statement {
        BoolExpression condition;
        StatementList loopBlock;

        public WhileStatement(BoolExpression condition, StatementList loopBlock){
            this.condition = condition;
            this.loopBlock = loopBlock;
        }

        public String toString(){
            return String.format("while (%s} {\n%s\n}", condition.toString(), loopBlock.toString());
        }
    }

    public static class DoWhileStatement extends Statement {
        BoolExpression condition;
        StatementList loopBlock;

        public DoWhileStatement(BoolExpression condition, StatementList loopBlock){
            this.condition = condition;
            this.loopBlock = loopBlock;
        }

        public String toString(){
            return String.format("do {\n%s\n} while (%s)", loopBlock.toString(), condition.toString());
        }
    }

    public static class ForEachStatement extends Statement {
        Expression expression;
        Variable variable;
        StatementList loopBlock;

        public ForEachStatement(Variable variable, Expression expression, StatementList loopBlock){
            this.variable = variable;
            this.expression = expression;
            this.loopBlock = loopBlock;
        }

        public String toString(){
            return String.format("for (%s %s in %s){\n%s\n}", variable.type, variable.name, expression.toString(), loopBlock.toString());
        }
    }

    public static class TryCatchStatement extends Statement {
        StatementList tryBlock;
        StatementList catchBlock;

        public TryCatchStatement(StatementList tryBlock, StatementList catchBlock){
            this.tryBlock = tryBlock;
            this.catchBlock = catchBlock;
        }

        public String toString(){
            String toString = String.format("try {\n%s\n}", tryBlock.toString());
            if (catchBlock != null){
                toString += String.format(" catch {\n%s\n}", tryBlock.toString());
            }
            return toString;
        }
    }

    public static class FunctionCall extends Statement implements Expression {
        String functionName;
        ArrayList<Expression> parameters;

        public FunctionCall(String functionName, Expression... parameters){
            this.functionName = functionName;
            this.parameters = new ArrayList<>(Arrays.asList(parameters));
        }

        @Override
        public String getEvalType() {
            return "";
        }

        @Override
        public boolean usesVariable(String variableName) {
            for (Expression e: parameters){
                if (e.usesVariable(variableName)){
                    return true;
                }
            }
            return false;
        }

        public String toString(){
            String toString = functionName + "(";
            for (Expression e: parameters){
                toString += e.toString() + ", ";
            }
            return toString + ")";
        }
    }

    public static interface Expression {
        public String getEvalType();
        public boolean usesVariable(String variableName);
    }

    public static interface BoolExpression extends Expression {
        public default String getEvalType(){return "bool";};
        public BoolExpression negate();
    }

    public enum BoolOperator {
        AND,OR,NOT;
    }

    public static class BoolOperation implements BoolExpression {
        BoolExpression b1;
        BoolExpression b2;
        BoolOperator operator;

        public BoolOperation(BoolExpression b1, BoolOperator operator, BoolExpression b2){
            this.b1 = b1;
            this.b2 = b2;
            this.operator = operator;
        }

        @Override
        public boolean usesVariable(String variableName) {
            return b1.usesVariable(variableName) || b2.usesVariable(variableName);
        }

        public String toString(){
            if (operator == BoolOperator.NOT){
                return String.format("NOT (%s)", b1.toString());
            }
            return String.format("(%s) %s (%s)", b1.toString(), operator.toString(), b2.toString());
        }

        @Override
        public BoolExpression negate() {
            if (operator == BoolOperator.NOT){
                return b1;
            } else if (operator == BoolOperator.AND){
                return new BoolOperation(b1.negate(), BoolOperator.OR, b2.negate());
            } else {
                return new BoolOperation(b1.negate(), BoolOperator.AND, b2.negate());
            }
        }
    }

    public enum RelOperator {
        LESS_THAN, LESS_THAN_EQUALS, GREATER_THAN,GREATER_THAN_EQUALS,EQUALS,NOT_EQUALS;
    }

    public static class ExpressionComparison implements BoolExpression {
        Expression e1;
        Expression e2;
        RelOperator operator;

        public ExpressionComparison(Expression e1, RelOperator operator, Expression e2){
            this.e1 = e1;
            this.e2 = e2;
            this.operator = operator;
        }
        @Override
        public boolean usesVariable(String variableName) {
            return e1.usesVariable(variableName) || e2.usesVariable(variableName);
        }

        public String toString(){
            return String.format("(%s) %s (%s)", e1.toString(), operator.toString(), e2.toString());
        }

        @Override
        public BoolExpression negate() {
            RelOperator op;
            if (operator == RelOperator.EQUALS){
                op = RelOperator.NOT_EQUALS;
            } else if (operator == RelOperator.LESS_THAN){
                op = RelOperator.GREATER_THAN_EQUALS;
            } else if (operator == RelOperator.GREATER_THAN){
                op = RelOperator.LESS_THAN_EQUALS;
            } else if (operator == RelOperator.NOT_EQUALS){
                op = RelOperator.EQUALS;
            } else if (operator == RelOperator.GREATER_THAN_EQUALS){
                op = RelOperator.LESS_THAN;
            } else if (operator == RelOperator.LESS_THAN_EQUALS){
                op = RelOperator.GREATER_THAN;
            } else {
                throw new RuntimeException("Relation Operator value is null");
            }
            return new ExpressionComparison(e1, op, e2);
        }
    }

    public static class BoolConst implements BoolExpression {
        boolean value;

        public BoolConst(boolean value){
            this.value = value;
        }

        @Override
        public boolean usesVariable(String variableName) {
            return false;
        }

        public String toString(){
            return String.valueOf(value);
        }

        @Override
        public BoolExpression negate() {
            return new BoolConst(!value);
        }
    }

    public enum NumericOperator {
        ADD,SUBTRACT,MULTIPLY,DIVIDE;
    }

    public static class NumericOperation implements Expression {
        Expression e1;
        Expression e2;
        NumericOperator operator;

        public NumericOperation(Expression e1, NumericOperator operator, Expression e2){
            this.e1 = e1;
            this.e2 = e2;
            this.operator = operator;
        }

        @Override
        public String getEvalType() {
            String type1 = e1.getEvalType();
            String type2 = e2.getEvalType();
            if (type1.isEmpty() || type2.isEmpty()){
                return "";
            } else if (type1.equals(type2)){
                return type1;
            } else if ((type1.equals("int") && type2.equals("float")) || (type1.equals("float") && type2.equals("int"))){
                return "float";
            }
            return "";
        }

        @Override
        public boolean usesVariable(String variableName) {
            return e1.usesVariable(variableName) || e2.usesVariable(variableName);
        }

        public String toString(){
            return String.format("(%s) %s (%s)", e1.toString(), operator.toString(), e2.toString());
        }
    }

    public static class Reference implements Expression {
        Variable variable;
        ArrayList<Expression> accessModifiers;

        public Reference(Variable variable, Expression... accessModifiers){
            this.variable = variable;
            this.accessModifiers = new ArrayList<>(Arrays.asList(accessModifiers));
        }

        @Override
        public String getEvalType() {
            String variableType = variable.type;
            if (!variableType.equals("array") && !variableType.equals("array2") && !variableType.equals("object")){
                throw new RuntimeException(String.format("Variable type %s cannot have access modifiers", variableType));
            }
            return "";
        }

        @Override
        public boolean usesVariable(String variableName) {
            if (variable.usesVariable(variableName)){
                return true;
            }
            for (Expression e: accessModifiers){
                if (e.usesVariable(variableName)){
                    return true;
                }
            }
            return false;
        }

        public String toString(){
            String toString = variable.name;
            if (!accessModifiers.isEmpty()){
                toString += "[";
                for (Expression e: accessModifiers){
                    toString += e.toString() + ", ";
                }
                toString += "]";
            }
            return toString;
        }
    }

    public static class IntConst implements Expression {
        int value;

        public IntConst(int value){
            this.value = value;
        }

        @Override
        public String getEvalType() {
            return "int";
        }

        @Override
        public boolean usesVariable(String variableName) {
            return false;
        }

        public String toString(){
            return String.valueOf(value);
        }
    }

    public static class FloatConst implements Expression {
        double value;

        public FloatConst(double value){
            this.value = value;
        }

        @Override
        public String getEvalType() {
            return "float";
        }

        @Override
        public boolean usesVariable(String variableName) {
            return false;
        }

        public String toString(){
            return String.valueOf(value);
        }
    }

    public static class StringConst implements Expression {
        String value;

        public StringConst(String value){
            this.value = value;
        }

        @Override
        public String getEvalType() {
            return "string";
        }

        @Override
        public boolean usesVariable(String variableName) {
            return false;
        }

        public String toString(){
            return String.format("\"%s\"", value);
        }
    }

    public static class ArrayConst implements Expression {
        ArrayList<Expression> value;

        public ArrayConst(Expression... value){
            this.value = new ArrayList<>(Arrays.asList(value));
        }

        @Override
        public String getEvalType() {
            return "array";
        }

        @Override
        public boolean usesVariable(String variableName) {
            for (Expression e: value){
                if (e.usesVariable(variableName)){
                    return true;
                }
            }
            return false;
        }

        public String toString(){
            String toString = "[";
            for (Expression e: value){
                toString += e.toString() + ", ";
            }
            return toString + "]";
        }
    }

    public static class ObjectConst implements Expression {
        HashMap<String, Expression> value;

        public ObjectConst(){
            value = new HashMap<>();
        }

        @Override
        public String getEvalType() {
            return "object";
        }

        @Override
        public boolean usesVariable(String variableName) {
            for (String key: value.keySet()){
                if (value.get(key).usesVariable(variableName)){
                    return true;
                }
            }
            return false;
        }

        public String toString(){
            String toString = "{";
            for (String key: value.keySet()){
                toString += String.format("%s: %s, ", key, value.get(key));
            }
            return toString + "}";
        }
    }

    public static class Variable implements Expression {
        String type;
        String name;

        public Variable(String type, String name){
            this.type = type;
            this.name = name;
        }

        @Override
        public String getEvalType() {
            return type;
        }

        @Override
        public boolean usesVariable(String variableName) {
            return name.equals(variableName);
        }
    }
}
