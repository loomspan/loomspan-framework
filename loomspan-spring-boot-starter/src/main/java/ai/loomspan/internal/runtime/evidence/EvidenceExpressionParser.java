package ai.loomspan.internal.runtime.evidence;

import java.util.ArrayList;
import java.util.List;

public final class EvidenceExpressionParser
{
    private String source;
    private int offset;
    private Token lookahead;

    public EvidenceExpression parse(String expression)
    {
        source = expression == null ? "" : expression;
        offset = 0;
        lookahead = nextToken();
        if (lookahead.type == TokenType.EOF)
        {
            throw error(lookahead, "expression must not be blank");
        }
        EvidenceExpression result = parseOr();
        if (lookahead.type != TokenType.EOF)
        {
            throw error(lookahead, lookahead.type == TokenType.RPAREN
                    ? "unmatched closing ')'"
                    : "expected 'and', 'or', or end of expression");
        }
        return result;
    }

    private EvidenceExpression parseOr()
    {
        List<EvidenceExpression> expressions = new ArrayList<>();
        expressions.add(parseAnd());
        while (lookahead.type == TokenType.OR)
        {
            consume();
            expressions.add(parseAnd());
        }
        return expressions.size() == 1 ? expressions.getFirst() : new EvidenceExpression.AnyOf(flattenAny(expressions));
    }

    private EvidenceExpression parseAnd()
    {
        List<EvidenceExpression> expressions = new ArrayList<>();
        expressions.add(parsePrimary());
        while (lookahead.type == TokenType.AND)
        {
            consume();
            expressions.add(parsePrimary());
        }
        return expressions.size() == 1 ? expressions.getFirst() : new EvidenceExpression.AllOf(flattenAll(expressions));
    }

    private EvidenceExpression parsePrimary()
    {
        Token token = lookahead;
        if (token.type == TokenType.IDENTIFIER)
        {
            consume();
            return new EvidenceExpression.Skill(token.text, token.start + 1);
        }
        if (token.type == TokenType.LPAREN)
        {
            consume();
            if (lookahead.type == TokenType.RPAREN)
            {
                throw error(lookahead, "empty parentheses are not allowed");
            }
            EvidenceExpression expression = parseOr();
            if (lookahead.type != TokenType.RPAREN)
            {
                throw error(lookahead, "expected ')' to close expression");
            }
            consume();
            return expression;
        }
        if (token.type == TokenType.AND || token.type == TokenType.OR)
        {
            throw error(token, "reserved operator '" + token.text + "' cannot be used as a skill reference");
        }
        if (token.type == TokenType.EOF)
        {
            throw error(token, "expected a skill name or '('");
        }
        throw error(token, "expected a skill name or '('");
    }

    private void consume()
    {
        lookahead = nextToken();
    }

    private Token nextToken()
    {
        while (offset < source.length() && Character.isWhitespace(source.charAt(offset)))
        {
            offset++;
        }
        if (offset >= source.length())
        {
            return new Token(TokenType.EOF, "", offset);
        }
        int start = offset;
        char current = source.charAt(offset);
        if (current == '(')
        {
            offset++;
            return new Token(TokenType.LPAREN, "(", start);
        }
        if (current == ')')
        {
            offset++;
            return new Token(TokenType.RPAREN, ")", start);
        }
        if (Character.isLetter(current) || current == '_')
        {
            offset++;
            while (offset < source.length())
            {
                char character = source.charAt(offset);
                if (!Character.isLetterOrDigit(character) && character != '_')
                {
                    break;
                }
                offset++;
            }
            String text = source.substring(start, offset);
            if ("and".equalsIgnoreCase(text))
            {
                return new Token(TokenType.AND, text, start);
            }
            if ("or".equalsIgnoreCase(text))
            {
                return new Token(TokenType.OR, text, start);
            }
            return new Token(TokenType.IDENTIFIER, text, start);
        }
        offset++;
        return new Token(TokenType.INVALID, Character.toString(current), start);
    }

    private ParseException error(Token token, String detail)
    {
        return new ParseException(token.start + 1, detail);
    }

    private static List<EvidenceExpression> flattenAll(List<EvidenceExpression> expressions)
    {
        List<EvidenceExpression> flattened = new ArrayList<>();
        expressions.forEach(expression ->
        {
            if (expression instanceof EvidenceExpression.AllOf allOf)
            {
                flattened.addAll(allOf.expressions());
            }
            else
            {
                flattened.add(expression);
            }
        });
        return flattened;
    }

    private static List<EvidenceExpression> flattenAny(List<EvidenceExpression> expressions)
    {
        List<EvidenceExpression> flattened = new ArrayList<>();
        expressions.forEach(expression ->
        {
            if (expression instanceof EvidenceExpression.AnyOf anyOf)
            {
                flattened.addAll(anyOf.expressions());
            }
            else
            {
                flattened.add(expression);
            }
        });
        return flattened;
    }

    private enum TokenType { IDENTIFIER, AND, OR, LPAREN, RPAREN, INVALID, EOF }

    private record Token(TokenType type, String text, int start) { }

    public static final class ParseException extends IllegalArgumentException
    {
        private final int column;

        ParseException(int column, String detail)
        {
            super(detail);
            this.column = column;
        }

        public int column()
        {
            return column;
        }
    }
}
