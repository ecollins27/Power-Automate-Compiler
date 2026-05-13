import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class CodeScanner {

    ArrayList<String[]> regexExpressions;
    String[] lines;
    int currentLine = 0, currentCharacter = 0;

    public CodeScanner(String filename) throws FileNotFoundException {
        File file = new File(filename);
        Scanner tokenFile = new Scanner(file);
        regexExpressions = new ArrayList<>();
        while (tokenFile.hasNextLine()) {
            String line = tokenFile.nextLine();
            String tokenType = line.split("->")[0];
            String regex = line.split("->")[1];
            regexExpressions.add(new String[]{regex, tokenType});
        }
    }

    public void setText(String text){
        lines = text.split("\n");
        System.out.println(Arrays.toString(lines));
    }

    public Token getNextToken(){
        if (currentCharacter >= lines[currentLine].length()){
            if (currentLine < lines.length - 1){
                currentLine++;
                currentCharacter = 0;
            } else {
                return null;
            }
        }
        char currentChar = lines[currentLine].charAt(currentCharacter);
        while (currentChar == ' ' || currentChar == '\t'){
            if (currentCharacter < lines[currentLine].length()){
                currentCharacter++;
            } else if (currentLine < lines.length - 1){
                currentCharacter = 0;
                currentLine++;
            } else {
                return null;
            }
            currentChar = lines[currentLine].charAt(currentCharacter);
        }
        String bestType = "";
        String bestSubString = "";
        for (int i = currentCharacter + 1; i <= lines[currentLine].length(); i++){
            String substring = lines[currentLine].substring(currentCharacter, i);
            boolean passed = false;
            for (String[] token: regexExpressions) {
                if (substring.matches(token[0])) {
                    passed = true;
                    bestType = token[1];
                    break;
                }
            }
            if (passed) {
                bestSubString = substring;
            }
        }
        if (bestSubString.isEmpty()){
            throw new RuntimeException("Unexpected character \"" + lines[currentLine].charAt(currentCharacter) + "\" on line " + currentLine + " at character " + currentCharacter);
        }
        currentCharacter += bestSubString.length();
        return new Token(bestSubString, bestType);
    }
}
