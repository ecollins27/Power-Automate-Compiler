import java.sql.Array;
import java.util.*;

public class ASTNode {

    Token token;
    String[] production;
    ASTNode[] children;

    public ASTNode(String type){
        token = new Token("", type);
    }

    public ASTNode(Token token){
        this.token = token;
    }

    public ASTNode[] setProduction(String[] production){
        this.production = production;
        ArrayList<ASTNode> childrenList = new ArrayList<>();
        for (int i = 0; i < production.length; i++){
            if (production[i].charAt(0) == '~'){
                ASTNode node = new ASTNode(production[i]);
                childrenList.add(node);
            } else {
                childrenList.add(new ASTNode(new Token(production[i], "const")));
            }
        }
        this.children = childrenList.toArray(new ASTNode[0]);
        return this.children;
    }

    public void trim(){
        if (production == null){
            return;
        }
        ArrayList<String> productionList = new ArrayList<>(Arrays.asList(production));
        ArrayList<ASTNode> childrenList = new ArrayList<>(Arrays.asList(children));
        for (int i = productionList.size() - 1; i >= 0; i--){
            if (productionList.get(i).charAt(0) != '~'){
                productionList.remove(i);
                childrenList.remove(i);
            }
        }
        production = productionList.toArray(new String[0]);
        children = childrenList.toArray(new ASTNode[0]);
        for (int i = 0; i < children.length;i++){
            if (children[i].children != null && children[i].children.length > 0 && children[i].children[0].token.tokenType().equals("~epsilon")){
                children[i] = new ASTNode(new Token("", "~epsilon"));
            } else {
                children[i].trim();
            }
        }
    }
}
