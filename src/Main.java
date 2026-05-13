import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

    public static void printTree(ASTNode node, int depth){
        for (int i = 0; i < depth; i++){
            System.out.print("  ");
        }
        System.out.println(node.token);
        if (node.children != null) {
            for (int i = 0; i < node.children.length; i++) {
                printTree(node.children[i], depth + 1);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        CodeScanner scanner = new CodeScanner("tokens");
        scanner.setText(new String(Files.readAllBytes(Paths.get("code.txt")), StandardCharsets.UTF_8));
        CodeParser parser = new CodeParser("grammar");
        SimplifiedASTNode.StatementList ast = parser.toAST(scanner);
        Optimizer.reorderAST(ast);
        System.out.println(ast);
    }

    //Figure out syntax for all major functions
}