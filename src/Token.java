public record Token(String token, String tokenType) {
    public String toString(){
        return "Token(" + token + "," + tokenType + ")";
    }

    public boolean matches(Token token){
        if (tokenType.charAt(0) == '~' && token.tokenType.charAt(0) == '~'){
            return tokenType.equals(token.tokenType);
        } else {
            return token.equals(token.token) && tokenType.equals(token.tokenType);
        }
    }
}
