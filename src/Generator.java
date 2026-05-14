import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class Generator {

    String directory;
    String flowName;
    String flowID;

    HashMap<SimplifiedASTNode.Statement, String> blockNames;

    public Generator(String flowName, String folderName){
        directory = "output/" + folderName;
        File directoryFile = new File(directory);
        directoryFile.mkdir();
        this.flowName = flowName;
        this.flowID = flowName.replace(" ", "_");
        blockNames = new HashMap<>();
    }

    public String getBlockName(String name){
        name = name.replace(' ', '_');
        if (!blockNames.values().contains(name)){
            return name;
        }
        int index = 2;
        while (blockNames.values().contains(name + Integer.toString(index))){
            index++;
        }
        return name + Integer.toString(index);
    }

    public void writeBoilerPlate() throws IOException {
        FileWriter manifest1 = new FileWriter(directory + "/manifest.json");
        StringWriter stringWriter = new StringWriter();
        useTemplate("manifest_template.txt", stringWriter, 0, "flow_name", flowName, "flow_id", flowID);
        manifest1.write(stringWriter.toString());
        manifest1.close();

        new File(directory + "/Microsoft.Flow").mkdir();
        new File(directory + "/Microsoft.Flow/flows").mkdir();

        FileWriter manifest2 = new FileWriter(directory + "/Microsoft.Flow/flows/manifest.json");
        manifest2.write(String.format("{\"packageSchemaVersion\":\"1.0\",\"flowAssets\":{\"assetPaths\":[\"%s\"]}", flowID));
        manifest2.close();

        new File(directory + "/Microsoft.Flow/flows/" + flowID).mkdir();
        FileWriter apisMap = new FileWriter(directory + String.format("/Microsoft.Flow/flows/%s/apisMap.json", flowID));
        apisMap.write("{}");
        apisMap.close();
        FileWriter connectionsMap = new FileWriter(directory + String.format("/Microsoft.Flow/flows/%s/connectionsMap.json", flowID));
        connectionsMap.write("{}");
        connectionsMap.close();
    }

    public void useTemplate(String templateName, StringWriter writer, int depth, String... args) throws IOException {
        File file = new File("templates/" + templateName);
        Scanner scanner = new Scanner(file);
        while (scanner.hasNextLine()){
            String line = scanner.nextLine();
            for (int i = 0; i < args.length; i+=2){
                line = line.replace("{{" + args[i] + "}}", args[i + 1]);
            }
            for (int i = 0; i < depth; i++){
                writer.write("  ");
            }
            writer.write(line + "\n");
        }
    }

    public String generateExpression(SimplifiedASTNode.Expression expression){
        if (expression instanceof SimplifiedASTNode.FunctionCall){
            String functionName = ((SimplifiedASTNode.FunctionCall) expression).functionName;
            if (functionName.startsWith("to_")) {
                functionName = functionName.substring(3);
            }
            String generated = functionName + "(";
            for (int i = 0; i < ((SimplifiedASTNode.FunctionCall) expression).parameters.size(); i++){
                generated += generateExpression(((SimplifiedASTNode.FunctionCall) expression).parameters.get(i));
                if (i < ((SimplifiedASTNode.FunctionCall) expression).parameters.size() - 1){
                    generated += ",";
                }
            }
            return generated + ")";
        } else if (expression instanceof SimplifiedASTNode.BoolOperation){
            SimplifiedASTNode.BoolOperation boolOperation = (SimplifiedASTNode.BoolOperation) expression;
            if (boolOperation.operator == SimplifiedASTNode.BoolOperator.AND){
                return String.format("and(%s,%s)", generateExpression(boolOperation.b1), generateExpression(boolOperation.b2));
            } else if (boolOperation.operator == SimplifiedASTNode.BoolOperator.OR){
                return String.format("or(%s,%s)", generateExpression(boolOperation.b1), generateExpression(boolOperation.b2));
            } else {
                return String.format("not(%s)", generateExpression(boolOperation.b1));
            }
        } else if (expression instanceof SimplifiedASTNode.ExpressionComparison){
            SimplifiedASTNode.ExpressionComparison expressionComparison = (SimplifiedASTNode.ExpressionComparison) expression;
            HashMap<SimplifiedASTNode.RelOperator, String> map = new HashMap<>();
            map.put(SimplifiedASTNode.RelOperator.EQUALS, "equals");
            if (expressionComparison.operator == SimplifiedASTNode.RelOperator.EQUALS){

            }
        }
        return null;
    }

    public String getDefaultValue(String type){
        switch (type){
            case "int":
                return "0";
            case "float":
                return "0.0";
            case "string":
                return "";
            case "array":
                return "[]";
            case "array2":
                return "[]";
            case "object":
                return "{}";
        }
        throw new RuntimeException(String.format("Unknown data type %s", type));
    }

    public String getRunAfter(SimplifiedASTNode.Statement previous){
        if (previous == null){
            return "";
        }
        String blockName = blockNames.get(previous);
        if (previous instanceof SimplifiedASTNode.TryCatchStatement){
            if (((SimplifiedASTNode.TryCatchStatement) previous).catchBlock == null){
                return String.format("\"%s\": [\"Succeeded\",\"Failed\"]", blockName);
            } else {
                blockName = blockName.split(",")[1];
            }
        }
        return String.format("\"%s\": [\"Succeeded\"]", blockName);
    }

    public void generateVariableDeclaration(SimplifiedASTNode.VariableDeclaration declaration, SimplifiedASTNode.Statement previous, StringWriter writer, int depth) throws IOException {
        String blockName = getBlockName(String.format("Initialize_Variable_%s", declaration.variable.name));
        declaration.blockName.value = blockName;
        blockNames.put(declaration, blockName);
        String runAfter = getRunAfter(previous);
        String variableValue = previous == null? getDefaultValue(declaration.variable.type):generateExpression(declaration.defaultValue);
        useTemplate("variable_declaration_template.txt", writer, depth, "block_name", blockName, "run_after", runAfter, "variable_name", declaration.variable.name, "variable_type", declaration.variable.type, "variable_value", variableValue);
    }

    public void generateTryCatch(SimplifiedASTNode.TryCatchStatement tryCatch, SimplifiedASTNode.Statement previous, StringWriter writer, int depth) throws IOException {
        String tryBlockName = getBlockName("Try");
        tryCatch.blockName.value = tryBlockName;
        String catchBlockName = tryCatch.catchBlock == null? null:getBlockName("Catch");
        if (catchBlockName == null){
            blockNames.put(tryCatch, tryBlockName);
        } else {
            blockNames.put(tryCatch, String.format("%s|%s", tryBlockName, catchBlockName));
        }
        StringWriter actions = new StringWriter();
        generateStatementList(tryCatch.tryBlock, actions, depth + 2);
        String runAfter = getRunAfter(previous);
        useTemplate("try_catch_template.txt", writer, depth, "block_name", tryBlockName, "run_after", runAfter, "actions", actions.toString());
        if (tryCatch.catchBlock != null){
            writer.write(",\n");
            actions = new StringWriter();
            generateStatementList(tryCatch.catchBlock, actions, depth + 2);
            runAfter = String.format("\"%s\": [\"Failed\"]", catchBlockName);
            useTemplate("try_catch_template.txt", writer, depth, "block_name", tryBlockName, "run_after", runAfter, "actions", actions.toString());
        }
    }

    public void generateDoWhile(SimplifiedASTNode.DoWhileStatement doWhile, SimplifiedASTNode.Statement previous, StringWriter writer, int depth) throws IOException {
        String doWhileName = getBlockName("Do_Until");
        doWhile.blockName.value = doWhileName;
        StringWriter actions = new StringWriter();
        generateStatementList(doWhile.loopBlock, actions, depth + 2);
        String runAfter = getRunAfter(previous);
        useTemplate("do_while_template.txt", writer, depth, "block_name", doWhileName, "run_after", runAfter, "actions", actions.toString(), "condition", generateExpression(doWhile.condition.negate()));
    }

    public void generateWhile(SimplifiedASTNode.WhileStatement whileStatement, SimplifiedASTNode.Statement previous, StringWriter writer, int depth) throws IOException {
        String whileName = getBlockName("While_Not");
        whileStatement.blockName.value = whileName;
        StringWriter actions = new StringWriter();
        generateStatementList(whileStatement.loopBlock, actions, depth + 2);
        String runAfter = getRunAfter(previous);
        useTemplate("while_template.txt", writer, depth, "block_name", whileName, "run_after", runAfter, "actions", actions.toString(), "condition", generateExpression(whileStatement.condition), "condition_negate", generateExpression(whileStatement.condition.negate()));
    }

    public void generateForEach(SimplifiedASTNode.ForEachStatement forEachStatement, SimplifiedASTNode.Statement previous, StringWriter writer, int depth) throws IOException {
        String forEachName = getBlockName("For_Each");
        forEachStatement.blockName.value = forEachName;
        StringWriter actions = new StringWriter();
        actions.write(forEachStatement.loopBlock.first == null? "":",\n");
        String runAfter = getRunAfter(previous);
        generateStatementList(forEachStatement.loopBlock, actions, depth + 2);
        useTemplate("for_each_template.txt", writer, depth, "block_name", forEachName, "run_after", runAfter, "actions", actions.toString(), "expression", generateExpression(forEachStatement.expression));
    }

    public void generateBlockFunctionCall(SimplifiedASTNode.FunctionCall functionCall, SimplifiedASTNode.Statement previous, StringWriter writer, int depth) throws IOException {
        String blockName = getBlockName(functionCall.functionName);
        String runAfter = getRunAfter(previous);
        switch (functionCall.functionName){
            case "Compose":
                useTemplate("compose_template.txt", writer, depth, "block_name", blockName, "run_after", runAfter, "inputs", generateExpression(functionCall.parameters.get(0)));
                break;
            case "IncrementVariable":
                useTemplate("increment_variable_template.txt", writer, depth, "block_name", blockName, "run_after", runAfter, "variable_name", ((SimplifiedASTNode.Variable)functionCall.parameters.get(0)).name, "increment", generateExpression(functionCall.parameters.get(1)));
                break;
            case "AppendtoStringVariable":
                useTemplate("append_string_template.txt", writer, depth, "block_name", blockName, "run_after", runAfter, "variable_name", ((SimplifiedASTNode.Variable)functionCall.parameters.get(0)).name, "increment", generateExpression(functionCall.parameters.get(1)));
                break;
            case "AppendtoArrayVariable":
                useTemplate("append_array_template.txt", writer, depth, "block_name", blockName, "run_after", runAfter, "variable_name", ((SimplifiedASTNode.Variable)functionCall.parameters.get(0)).name, "increment", generateExpression(functionCall.parameters.get(1)));
                break;
            case "to_html":
                useTemplate("to_html_template.txt", writer, depth, "block_name", blockName, "run_after", runAfter, "value", generateExpression(functionCall.parameters.get(0)));
                break;
            case "to_csv":
                useTemplate("to_csv_template.txt", writer, depth, "block_name", blockName, "run_after", runAfter, "value", generateExpression(functionCall.parameters.get(0)));
                break;
            case "send_email":
                if (functionCall.parameters.size() > 4) {
                    useTemplate("to_email_template.txt", writer, depth, "block_name", blockName, "run_after", runAfter, "from", generateExpression(functionCall.parameters.get(0)), "to", generateExpression(functionCall.parameters.get(1)), "subject", generateExpression(functionCall.parameters.get(2)), "contents", generateExpression(functionCall.parameters.get(3)), "attachment_name", generateExpression(functionCall.parameters.get(4)), "attachment_contents", generateExpression(functionCall.parameters.get(5)));
                } else {
                    useTemplate("to_email_template.txt", writer, depth, "block_name", blockName, "run_after", runAfter, "from", generateExpression(functionCall.parameters.get(0)), "to", generateExpression(functionCall.parameters.get(1)), "subject", generateExpression(functionCall.parameters.get(2)), "contents", generateExpression(functionCall.parameters.get(3)));
                }
            default:
                throw new RuntimeException(String.format("Unknwon function %s", functionCall.functionName));
        }
    }

    public void generateIf(SimplifiedASTNode.IfStatement ifStatement, SimplifiedASTNode.Statement previous, StringWriter writer, int depth) throws IOException {
        String ifName = getBlockName("If_Statement");
        StringWriter trueActions = new StringWriter();
        StringWriter falseActions = new StringWriter();
        generateStatementList(ifStatement.trueBlock, trueActions, depth + 2);
        if (ifStatement.falseBlock != null){
            generateStatementList(ifStatement.falseBlock, falseActions, depth + 3);
        }
        String runAfter = getRunAfter(previous);
        useTemplate("if_template.txt", writer, depth, "block_name", ifName, "run_after", runAfter, "true_actions", trueActions.toString(), "false_actions", falseActions.toString(), "condition", generateExpression(ifStatement.condition));
    }

    public void generateAppendToString(SimplifiedASTNode.Variable variable, SimplifiedASTNode.Expression expression, SimplifiedASTNode.Statement previous, StringWriter writer, int depth){

    }

    public void generateIncrementVariable(SimplifiedASTNode.Variable variable, SimplifiedASTNode.Expression expression, SimplifiedASTNode.Statement previous, StringWriter writer, int depth){

    }

    public void generateReferenceAssignment(SimplifiedASTNode.ReferenceAssignment refAssignment, SimplifiedASTNode.Statement previous, StringWriter writer, int depth){
        String refAssignmentName = getBlockName(String.format("Set_Variable_%s", refAssignment.variable.name));
        SimplifiedASTNode.Variable variable = refAssignment.variable;

    }

    public void generateStatement(SimplifiedASTNode.Statement statement, SimplifiedASTNode.Statement previous, StringWriter writer, int depth) throws IOException {
        if (statement instanceof SimplifiedASTNode.VariableDeclaration){
            generateVariableDeclaration((SimplifiedASTNode.VariableDeclaration) statement, previous, writer, depth);
        } else if (statement instanceof SimplifiedASTNode.ReferenceAssignment){
            generateReferenceAssignment((SimplifiedASTNode.ReferenceAssignment) statement, previous, writer, depth);
        } else if (statement instanceof SimplifiedASTNode.IfStatement){
            generateIf((SimplifiedASTNode.IfStatement) statement, previous, writer, depth);
        } else if (statement instanceof SimplifiedASTNode.WhileStatement){
            generateWhile((SimplifiedASTNode.WhileStatement) statement, previous, writer, depth);
        } else if (statement instanceof SimplifiedASTNode.DoWhileStatement){
            generateDoWhile((SimplifiedASTNode.DoWhileStatement) statement, previous, writer, depth);
        } else if (statement instanceof SimplifiedASTNode.ForEachStatement){
            generateForEach((SimplifiedASTNode.ForEachStatement) statement, previous, writer, depth);
        } else if (statement instanceof SimplifiedASTNode.FunctionCall){
            generateBlockFunctionCall((SimplifiedASTNode.FunctionCall) statement, previous, writer, depth);
        } else {
            throw new RuntimeException(String.format("Unknown statement type %s", statement.getClass().toString()));
        }
    }

    public void generateStatementList(SimplifiedASTNode.StatementList statementList, StringWriter writer, int depth) throws IOException {
        SimplifiedASTNode.Statement currentStatement = statementList.first;
        while (currentStatement != null){
            generateStatement(currentStatement, currentStatement.prev, writer, depth);
            currentStatement = currentStatement.next;
            if (currentStatement != null){
                writer.write(",\n");
            }
        }
    }

    public void generate(SimplifiedASTNode.StatementList rootNode) throws IOException {
        writeBoilerPlate();
        FileWriter definitions = new FileWriter(directory + String.format("/Microsoft.Flow/flows/%s/definition.json", flowID));
        StringWriter actions = new StringWriter();
        StringWriter writer = new StringWriter();
        generateStatementList(rootNode, actions, 5);
        useTemplate("definitions_template.txt", writer, 0, "flow_id", flowID, "actions", actions.toString());
        definitions.write(writer.toString());
        definitions.close();
    }
}
