/**
* 
* This is a very simple JSON parser, that can parse raw JSON string and convert them to Java Map.
* There is no error handling and everything is packed inside a single java file.
* Prefer to main function for examples usage.
* 
 */

package main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class SimpleJson {
	
	private static final char OBJECT_BEGIN_CHAR = '{';
	private static final char OBJECT_END_CHAR = '}';
	private static final char ARRAY_BEGIN_CHAR = '[';
	private static final char ARRAY_END_CHAR = ']';
	private static final char STRING_CHAR = '"';
	private static final char KEY_SEPERATOR_CHAR = ':';
	private static final char VALUE_SEPERATOR_CHAR = ',';
	private static final char NUMBER_SEPERATOR_CHAR = '.';
	
	private static enum TType {
		OBJECT_BEGIN,
		OBJECT_END,
		ARRAY_BEGIN,
		ARRAY_END,
		STRING,
		KEY_SEPERATOR,
		VALUE_SEPERATOR,
		NUMBER,
		NULL,
		TRUE,
		FALSE
		
	}
	
	private static class Token {
		final TType type;
		final String lexeme;
		final Object literal;
		final int line;
		
		private Token(TType type, String lexeme, Object literal, int line) {
			this.type = type;
			this.lexeme = lexeme;
			this.literal = literal;
			this.line = line;
		}
		
		public String toString() {
			return "[line" + line +  "]" + type + " " + lexeme + " " + literal;
		}
		
	}
	
	private static class Scanner {
		private List<Token> tokens;
		private int start = 0;
		private int current = 0;
		private int line = 1;
		private String source;
		private static final Map<String, TType> keywords;
		
		static {
			keywords = new HashMap<String, TType>();
			keywords.put("null", TType.NULL);
			keywords.put("true", TType.TRUE);
			keywords.put("false", TType.FALSE);
		}
		
		private static Scanner getScanner(String source) {
			return new Scanner(source);
		}
		
		private Scanner() {}
		
		private Scanner(String source) {
			this.source = source;
			this.tokens = new ArrayList<Token>();
		}
		
		private List<Token> scanTokens() {
			while(!isEOF()) {
				start = current;
				scanToken();
			}
			return tokens;
		}
		
		private void scanToken() {
			char c = advance();
			switch(c) {
			case ' ':
			case '\r':
			case '\t':
				break;
			case OBJECT_BEGIN_CHAR:
				object();
				break;
			case ARRAY_BEGIN_CHAR:
				array();
				break;
			case STRING_CHAR:
				string();
				break;
			case KEY_SEPERATOR_CHAR:
				addToken(TType.KEY_SEPERATOR);
				break;
			case VALUE_SEPERATOR_CHAR:
				addToken(TType.VALUE_SEPERATOR);
				break;
			case '\n':
				line++;
				break;
			default:
				if(isDigit(c)) {
					number();
				}else if(isAlpha(c)) {
					identifier();
				}else {
					throw new RuntimeException("Unexpected token.");
				}
				break;
			}
		}
		
		private void object() {
			addToken(TType.OBJECT_BEGIN);
			while(!isEOF() && peek(0) != OBJECT_END_CHAR) {
				start = current;
				scanToken();
			}
			if(!match(OBJECT_END_CHAR)) throw new RuntimeException("Unterminated object.");
			start = current - 1;
			addToken(TType.OBJECT_END);
		}
		
		private void array() {
			addToken(TType.ARRAY_BEGIN);
			while(!isEOF() && peek(0) != ARRAY_END_CHAR) {
				start = current;
				scanToken();
			}
			if(!match(ARRAY_END_CHAR)) throw new RuntimeException("Unterminated array.");
			start = current - 1;
			addToken(TType.ARRAY_END);
		}
		
		private void string() {
			while(!isEOF() && peek(0) != STRING_CHAR) {
				if(peek(0) == '\n') line++;
				advance();
			}
			if(isEOF()) throw new RuntimeException("Unterminated string.");
			advance();
			
			String value = source.substring(start + 1, current - 1);
			addToken(TType.STRING, value);
		}
		
		private void number() {
			while(isDigit(peek(0))) advance();
			
			boolean isInteger = true;
			
			if(peek(0) == NUMBER_SEPERATOR_CHAR && isDigit(peek(1))) {
				advance();
				isInteger = false;
				while(isDigit(peek(0))) advance();
			}
			Number literal;
			if(isInteger) literal = Integer.parseInt(source.substring(start, current)); 
			else literal = Double.parseDouble(source.substring(start, current));
			addToken(TType.NUMBER, literal);
		}
		
		private void identifier() {
			while(isAlphaNumeric(peek(0))) advance();
			
			String text = source.substring(start, current);
			TType type = keywords.get(text);
			if(text == null) type = TType.STRING;
			
			if(type == TType.STRING) addToken(type, text);
			else addToken(type);
		}
		
		private char advance() {
			return source.charAt(current++);
		}
		
		private char peek(int offset) {
			if(current + offset >= source.length()) return '\0';
			return source.charAt(current + offset);
		}
		
		private boolean match(char expected) {
			if(isEOF()) return false;
			if(peek(0) != expected) return false;
			advance();
			return true;
		}
		
		
		private boolean isDigit(char c) {
			return c >= '0' && c <= '9';
		}
		
		private boolean isAlpha(char c) {
			return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
		}
		
		private boolean isAlphaNumeric(char c) {
			return isDigit(c) || isAlpha(c);
		}
		
		private boolean isEOF() {
			return current >= source.length();
		}
		
		
		private void addToken(TType type) {
			addToken(type, null);
		}
		
		private void addToken(TType type, Object literal) {
			String text = source.substring(start, current);
			tokens.add(new Token(type, text, literal, line));
		}
	
	}
	
	private static class Parser {
		
		private Map<String, Object> parent;
		private Object child;
		private Stack<Object> stack;
		private List<Token> tokens;
		private Token prevToken;
		private Token currToken;
		private int i;
		
		private Parser() {}
		
		private Parser(List<Token> tokens) {
			this.parent = new LinkedHashMap<String, Object>();
			this.stack = new Stack<Object>();
			this.tokens = tokens;
		}
		
		
		private static Parser getParser(List<Token> tokens) {
			return new Parser(tokens);
		}
		
		private Map<String, Object> parse() {
			if(tokens.size() == 0) return parent;
			
			prevToken = tokens.get(0);
			
			while(!isEnd()) {
				parseToken();
			}
			
			return parent;
		}
		
		private void parseToken() {
			currToken = tokens.get(i++);
			child = stack.isEmpty() ? parent : stack.peek();
			
			switch(currToken.type) {
			case OBJECT_BEGIN:
				parseObject();
				break;
			case STRING:
				parseString();
				break;
			case NUMBER:
				parseNumber();
				break;
			case VALUE_SEPERATOR:
				break;
			case ARRAY_BEGIN:
				parseArray();
				break;
			case KEY_SEPERATOR:
				break;
			case OBJECT_END:
				if(!stack.isEmpty()) {
					stack.pop();
				}
				break;
			case ARRAY_END:
				if(!stack.isEmpty()) {
					stack.pop();
				}
				break;
			case NULL:
				parseNull();
				break;
			case TRUE:
				parseTrue();
				break;
			case FALSE:
				parseFalse();
				break;
			default:
				throw new RuntimeException("Unexpected token.");
			}
			
			if(currToken.type != TType.KEY_SEPERATOR) prevToken = currToken;
		}
		
		private void parseObject() {
			putValue(new LinkedHashMap<String, Object>());
			
			if(prevToken.type == TType.STRING) {
				if(child instanceof Map) {
					stack.push(((Map<String, Object>) child).get(prevToken.literal.toString()));
				}
			}
			
			if(child instanceof List) {
				stack.push(((List<Object>) child).get(((List<Object>) child).size() - 1));
			}
			
			prevToken = currToken;
			
			while(!isEnd() && currToken.type != TType.OBJECT_END) {
				parseToken();
			}
			
		}
		
		private void parseArray() {
			putValue(new ArrayList<Object>());
			
			if(prevToken.type == TType.STRING) {
				if(child instanceof Map) {
					stack.push(((Map<String, Object>) child).get(prevToken.literal.toString()));
				}
			}
			
			if(child instanceof List) {
				stack.push(((List<Object>) child).get(((List<Object>) child).size() - 1));
			}
			
			prevToken = currToken;
			
			while(!isEnd() && currToken.type != TType.ARRAY_END) {
				parseToken();
			}
		}
		
		private void parseString() {
			putValue(currToken.literal.toString());
		}
		
		private void parseNumber() {
			putValue(currToken.literal);
		}
		
		private void parseNull() {
			putValue(null);
		}
		
		private void parseTrue() {
			putValue(true);
			
		}
		
		private void parseFalse() {
			putValue(false);
			
		}
		
		private void putValue(Object v) {
			
			if(prevToken.type == TType.STRING) {
				if(child instanceof Map) {
					((Map<String, Object>) child).put(prevToken.literal.toString(), v);
				}
			}
			
			if(child instanceof List) {
				((List<Object>) child).add(v);
			}
			
		}
		
		private boolean isEnd() {

			return i >= tokens.size();
		}
	}
	
	private static class Printer {
		
		private Map<String, Object> json;
		private StringBuilder builder;
		private String indent = "";
		
		private Printer() {}
		
		private Printer(Map<String, Object> json) {
			this.json = json;
			this.builder = new StringBuilder();
		}
		
		private static void print(Map<String, Object> json, int indent) {
			new Printer(json).print(indent);
		}
		
		private static String serialize(Map<String, Object> json) {
			return serialize(json, 2);
		}
		
		private static String serialize(Map<String, Object> json, int indent) {
			Printer p = new Printer(json);
			for(int i = 0; i < indent; i++) p.indent += " ";
			p.buildString(json, 1, false);
			return p.builder.toString();
		}
		
		
		private void print(int indent) {
			for(int i = 0; i < indent; i++) this.indent += " ";
			buildString(json, 1, false);
			System.out.println(builder.toString());
		}
		
		private void buildString(Map<String, Object> json, int levels, boolean hasNext) {
			builder.append("{");
			builder.append("\n");
			Iterator<String> iter = json.keySet().iterator();
			while(iter.hasNext()) {
				String key = iter.next();
				for(int i = 0; i < levels; i++) {
					builder.append(this.indent);
				}
				Object value = json.get(key);
				
				if(value instanceof Map) {
					builder.append(String.format("\"%s\": ", key));
					buildString((Map<String, Object>) value, levels + 1, iter.hasNext());
				}
				else if(value instanceof List) {
					buildArrayString((List<Object>) value, levels, key);
				}
				else {
					builder.append(String.format("\"%s\": %s", key, value instanceof String ? "\"" + value.toString() + "\"" : value));
					if(iter.hasNext()) builder.append(",");
					builder.append("\n");
				}
			}
			if(levels != 1) {
				for(int i = 0; i < levels - 1; i++) {
					builder.append(this.indent);
				}
			}
			builder.append("}");
			if(hasNext) builder.append(",");
			builder.append("\n");
			
		
		}
		
		private void buildArrayString(List<Object> list, int levels, String key) {
			if(key == null) builder.append("[");
			else builder.append(String.format("\"%s\": [", key));
			builder.append("\n");
			
			Iterator<Object> iter = list.iterator();
			while(iter.hasNext()) {
				Object item = iter.next();
				for(int i = 0; i < levels + 1; i++) {
					builder.append(this.indent);
				}
				if(item instanceof List) {
					buildArrayString((List<Object>) item, levels + 1, null);
				}else if(item instanceof Map) {
					buildString((Map<String, Object>) item, levels + 2, iter.hasNext());
				}else {
					builder.append(item instanceof String ? "\"" + item.toString() + "\"" : item);
					if(iter.hasNext()) builder.append(",");
					builder.append("\n");
				}
			}
			
			for(int i = 0; i < levels; i++) {
				builder.append(this.indent);
			}
			builder.append("]");
			builder.append("\n");
		}
	}
	
	public static void print(Map<String, Object> json) {
		print(json, 2);
	}
	
	public static void print(Map<String, Object> json, int indent) {
		Printer.print(json, indent);
	}
	
	public static Map<String, Object> parse(String json) {
		return Parser.getParser(Scanner.getScanner(json).scanTokens()).parse();	
	}
	
	public static String serialize(Map<String, Object> json) {
		return Printer.serialize(json);
	}
	
	public static void main(String[] args) {
		
		String[] examples = new String[3];
		examples[0] = "[   \"One   \" , 123  , \"   Two  \"   ,   \"    Three   \", 12131.131312, 5113.111, [[1, 3, 5,2,3,4]   ], {\"a\": 123, \"b\": \"c\"}]";
		examples[1] = "{\r\n" + 
				"    \"glossary\": {\r\n" + 
				"        \"title\": \"example glossary\",\r\n" + 
				"		\"GlossDiv\": {\r\n" + 
				"            \"title\": \"S\",\r\n" + 
				"			\"GlossList\": {\r\n" + 
				"                \"GlossEntry\": {\r\n" + 
				"                    \"ID\": \"SGML\",\r\n" + 
				"					\"SortAs\": \"SGML\",\r\n" + 
				"					\"GlossTerm\": \"Standard Generalized Markup Language\",\r\n" + 
				"					\"Acronym\": \"SGML\",\r\n" + 
				"					\"Abbrev\": \"ISO 8879:1986\",\r\n" + 
				"					\"GlossSee\": \"markup\"\r\n" + 
				"                },\r\n" + 
				"                \"GlossEntry2\": {\r\n" + 
				"                    \"ID\": \"SGML\",\r\n" + 
				"					\"SortAs\": \"SGML\",\r\n" + 
				"					\"GlossTerm\": \"Standard Generalized Markup Language\",\r\n" + 
				"					\"Acronym\": \"SGML\",\r\n" + 
				"					\"Abbrev\": \"ISO 8879:1986\",\r\n" + 
				"					\"GlossSee\": \"markup\"\r\n" + 
				"                },\r\n" + 
				"                \"GlossEntry3\": {\r\n" + 
				"                    \"ID\": \"SGML\",\r\n" + 
				"					\"SortAs\": \"SGML\",\r\n" + 
				"					\"GlossTerm\": \"Standard Generalized Markup Language\",\r\n" + 
				"					\"Acronym\": \"SGML\",\r\n" + 
				"					\"Abbrev\": \"ISO 8879:1986\",\r\n" + 
				"					\"GlossSee\": \"markup\"\r\n" + 
				"                }\r\n" + 
				"            }\r\n" + 
				"        }\r\n" + 
				"    }\r\n" + 
				"}";
		examples[2] = "{\"name\":\"John\", \"age\": true, \"city\":\"New York\", \"a\": {\"b\": {\"c\": 123.123, \"e\": [\"ok bummer\",1,2,3,4,5, [5,6,7,8,9, {\"vip\": \"pro\", \"ok\": [1,2,3, {\"m\": {\"n\": {\"a\": [1,2,3,4,5]}}}]}, \"hello world\"]]}}, \"d\": 10}";
		//System.out.println(parse(examples[0]));
		//System.out.println(parse(examples[1]));
		System.out.println(serialize(parse(examples[2])));
	}
	
}
