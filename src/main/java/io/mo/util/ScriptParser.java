package io.mo.util;

import io.mo.cases.SqlCommand;
import io.mo.cases.TestScript;
import io.mo.constant.COMMON;
import org.apache.log4j.Logger;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ScriptParser {
    private TestScript testScript;
    private static final Logger LOG = Logger.getLogger(ScriptParser.class.getName());

    public ScriptParser() {
        this.testScript = new TestScript();
    }

    public TestScript parseScript(String path){
        testScript = new TestScript();
        testScript.setFileName(path);
        int rowNum = 1;
        try {
            BufferedReader lineReader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(path))));
            SqlCommand command = new SqlCommand();
            String line = lineReader.readLine();
            String trimmedLine;
            String issueNo = null;
            boolean ignore = false;
            int con_id = 0;
            String con_user = null;
            String con_pswd = null;

            while (line != null) {
                line = new String(line.getBytes(), StandardCharsets.UTF_8);
                trimmedLine = line.trim();
                //trimmedLine = line.replaceAll("\\s+$", "");

                //extract sql commands from the script file
                if (trimmedLine.equals("") || lineIsComment(trimmedLine)) {
                    
                    //if line is  mark to need to be skipped
                    if(trimmedLine.startsWith(COMMON.BVT_SKIP_FILE_FLAG)) {
                        issueNo = trimmedLine.substring(COMMON.BVT_SKIP_FILE_FLAG.length());
                        testScript.setSkiped(true);
                        LOG.info(String.format("The script file [%s] is marked to be skiped for issue#%s, and it will not be executed.",path,issueNo));
                        return testScript;
                    }
                    
                    //if line is  mark to relate to a bvt issue
                    //deal the tag bvt:issue:{issue number},when cases with this tag,will be ignored
                    if(trimmedLine.startsWith(COMMON.BVT_ISSUE_START_FLAG) && COMMON.IGNORE_MODEL) {
                        issueNo = trimmedLine.substring(COMMON.BVT_ISSUE_START_FLAG.length());
                        ignore = true;
                    }
                    
                    if(trimmedLine.equalsIgnoreCase(COMMON.BVT_ISSUE_END_FLAG)) {
                        issueNo = null;
                        ignore = false;
                    }

                    if(trimmedLine.startsWith(COMMON.FUNC_SLEEP_FLAG)){
                        int time = Integer.parseInt(trimmedLine.substring(COMMON.FUNC_SLEEP_FLAG.length()));
                        command.setSleeptime(time);
                    }

                    if(trimmedLine.startsWith(COMMON.SYSTEM_CMD_FLAG)){
                        String sysCmd = trimmedLine.substring(COMMON.SYSTEM_CMD_FLAG.length());
                        command.addSysCMD(sysCmd);
                    }

                    if(trimmedLine.startsWith(COMMON.REGULAR_MATCH_FLAG)){
                        command.setRegularMatch(true);
                    }
                    
                    if(trimmedLine.startsWith(COMMON.IGNORE_COLUMN_FLAG)){
                        String ignores = trimmedLine.substring(COMMON.IGNORE_COLUMN_FLAG.length());
                        if(ignores != null && !ignores.equalsIgnoreCase("")){
                            String[] ignore_ids = ignores.split(",");
                            for(int i = 0; i < ignore_ids.length;i++){
                                command.addIgnoreColumn(Integer.parseInt(ignore_ids[i]));
                            }
                        }
                    }

                    //if line is mark to set wait paras
                    if(trimmedLine.startsWith(COMMON.WAIT_FLAG)){
                        String[] items = trimmedLine.split(":");
                        //if flas is not formated like <-- @wait:1:commit >, ignore
                        if(items.length != 3)
                            continue;
                        else{
                            if(StringUtils.isNumeric(items[1])){
                                command.setWaitConnId(Integer.parseInt(items[1]));
                            }else {
                                LOG.warn(String.format("The connection id in flag[%s] is not a number, the flag is not valid.",trimmedLine));
                                continue;
                            }
                            
                            if(items[2].equalsIgnoreCase("commit") || 
                                    items[2].equalsIgnoreCase("rollback")){
                                command.setWaitOperation(items[2]);
                            }else {
                                LOG.warn(String.format("The operation in flag[%s] is not [commit] or [rollback], the flag is not valid.",trimmedLine));
                                continue;
                            }
                            
                            command.setNeedWait(true);
                        }
                    }
                    
                    //if line is mark to start a new connection
                    if(trimmedLine.startsWith(COMMON.NEW_SESSION_START_FLAG)){

                        String conInfo = null;
                        if(trimmedLine.endsWith("{"))
                            conInfo = trimmedLine.substring(COMMON.NEW_SESSION_START_FLAG.length(),trimmedLine.length() - 1);
                        else
                            conInfo = trimmedLine.substring(COMMON.NEW_SESSION_START_FLAG.length(),trimmedLine.length());
                        
                        if (conInfo == null || conInfo.equalsIgnoreCase("")){
                            LOG.warn("["+path+"][row:"+rowNum+"]The new connection flag doesn't designate the connection id by [id=X],and the id will be set to default value 1");
                            //command.setConn_id(COMMON.NEW_SEESION_DEFAULT_ID);
                            con_id = COMMON.NEW_SEESION_DEFAULT_ID;
                        }else {
                            String[] paras = conInfo.split("&");
                            for (String para:paras) {
                                if(para.startsWith("id=")){
                                    String id = para.substring(3);
                                    if(id.equalsIgnoreCase("")){
                                        LOG.warn("["+path+"][row:"+rowNum+"]The new connection flag doesn't designate the connection id by [id=X],and the id will be set to default value 1");
                                        //command.setConn_id(COMMON.NEW_SEESION_DEFAULT_ID);
                                        con_id = COMMON.NEW_SEESION_DEFAULT_ID;
                                    }else{
                                        if(id.matches("[0-9]+")){
                                            con_id = Integer.parseInt(id);
                                        }else {
                                            LOG.warn("["+path+"][row:"+rowNum+"]The new connection flag designate a invalid connection id by [id=X],and the id will be set to default value 1");
                                            //command.setConn_id(COMMON.NEW_SEESION_DEFAULT_ID);
                                            con_id = COMMON.NEW_SEESION_DEFAULT_ID;
                                        }
                                    }
                                }
                                
                                if(para.startsWith("user=")){
                                    String user = para.substring(5);
                                    if(user.equalsIgnoreCase("")){
                                        LOG.warn("["+path+"][row:"+rowNum+"]The new connection flag doesn't designate the connection user by [user=X],and the id will be set to value from mo.yml");
                                        //command.setConn_user(MoConfUtil.getUserName());
                                        con_user = MoConfUtil.getUserName();
                                    }else {
                                        //command.setConn_user(user);
                                        con_user = user;
                                    }
                                }
                                
                                if(para.startsWith("password=")){
                                    String pwd = para.substring(9);
                                    if(pwd.equalsIgnoreCase("")){
                                        LOG.warn("["+path+"][row:"+rowNum+"]The new connection flag doesn't designate the connection password by [password=X],and the id will be set to value from mo.yml");
                                        //command.setConn_pswd(MoConfUtil.getUserpwd());
                                        con_pswd = MoConfUtil.getUserpwd();
                                    }else {
                                        //command.setConn_pswd(pwd);
                                        con_pswd = pwd;
                                    }
                                }
                            }
                        }
                    }

                    if(trimmedLine.equalsIgnoreCase(COMMON.NEW_SESSION_END_FLAG) || trimmedLine.equalsIgnoreCase(COMMON.NEW_SESSION_END_FLAG + "}")){
                        con_id = 0;
                        con_user = null;
                        con_pswd = null;
                    }

                    //if line is mark to set sort key index
                    if(trimmedLine.startsWith(COMMON.SORT_KEY_INDEX_FLAG)){
                        String[] indexes = trimmedLine.replaceAll(COMMON.SORT_KEY_INDEX_FLAG,"").split(",");
                        for (String index : indexes) {
                            command.addSortKeyIndex(Integer.parseInt(index));
                        }
                    }

                    //if line is mark to set column separator
                    if(trimmedLine.startsWith(COMMON.COLUMN_SEPARATOR_FLAG)){
                        String separator = trimmedLine.replaceAll(COMMON.COLUMN_SEPARATOR_FLAG,"");
                        command.setSeparator(separator);
                    }

                    line = lineReader.readLine();
                    rowNum++;
                    continue;
                }
                
                // Check if delimiter is at the end of line and not inside a string
                // Pass accumulated command as context to handle multi-line strings
                String accumulatedCommand = command.getCommand();
                if (trimmedLine.contains("t_insert_test VALUES (1")) {
                    System.out.println(String.format("accumulatedCommand: [%s]", accumulatedCommand));
                    System.out.println(String.format("Start to parse the script file: [%s]", rowNum + ": " + trimmedLine));
                }
                if(isDelimiterAtLineEnd(accumulatedCommand != null ? accumulatedCommand : "", trimmedLine)){
                    command.append(trimmedLine);
                    
                    command.setConn_id(con_id);
                    command.setConn_user(con_user);
                    command.setConn_pswd(con_pswd);
                    command.setIgnore(ignore);
                    command.setIssueNo(issueNo);
                    command.setPosition(rowNum);
                    testScript.addCommand(command);
                    command = new SqlCommand();
                    // Skip the append below since we've already added the line and completed the command
                    line = lineReader.readLine();
                    rowNum++;
                    continue;
                }

                // just append the line to the command
                command.append(trimmedLine);
                command.append(COMMON.LINE_SEPARATOR);

                // read the next line
                line = lineReader.readLine();
                rowNum++;
            }
        } catch (IOException e) {
            LOG.error("Failed to parse script: " + path, e);
            throw new RuntimeException("Failed to parse script: " + path, e);
        }
        return testScript;
    }

    private boolean lineIsComment(String trimmedLine) {
        return trimmedLine.startsWith("//") || trimmedLine.startsWith("--") || trimmedLine.startsWith("#");
    }

    /**
     * Check if delimiter is at the end of line and not inside a string literal.
     * This method handles complex cases including nested quotes, overlapping quotes,
     * and multi-line strings.
     * 
     * <p><b>Algorithm:</b>
     * <ol>
     *   <li>Track string state from accumulated command (for multi-line strings)</li>
     *   <li>Continue tracking in current line</li>
     *   <li>Find the last delimiter position (from end, ignoring inline comments)</li>
     *   <li>If delimiter is outside any string literal, return true</li>
     * </ol>
     * 
     * <p><b>Examples:</b>
     * <ul>
     *   <li>{@code "SELECT * FROM t1;"} → returns {@code true}</li>
     *   <li>{@code "INSERT INTO t1 VALUES ('hello;world');"} → returns {@code true}</li>
     *   <li>{@code "INSERT INTO t1 VALUES (6, '`~\"\''\\');"} → returns {@code true} (handles overlapping quotes)</li>
     *   <li>{@code accumulated: "INSERT INTO t1 VALUES ('\n", current: "');"} → returns {@code true} (multi-line string closed)</li>
     *   <li>{@code "SELECT 'test;'"} → returns {@code false} (delimiter inside string)</li>
     *   <li>{@code "SELECT \"test;\""} → returns {@code false} (delimiter inside string)</li>
     * </ul>
     * 
     * @param accumulatedCommand the accumulated command from previous lines (for multi-line string tracking)
     * @param currentLine the current line to check
     * @return true if delimiter is at end of line and not in a string, false otherwise
     */
    private boolean isDelimiterAtLineEnd(String accumulatedCommand, String currentLine) {
        String trimmed = currentLine.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        
        // Find the last delimiter position, ignoring inline comments
        int delimiterPos = findLastDelimiterPosition(trimmed);
        if (delimiterPos == -1) {
            return false;
        }
        
        // Check if delimiter is inside any string literal, considering accumulated command
        return !isInsideStringLiteral(accumulatedCommand, trimmed, delimiterPos);
    }
    
    /**
     * Find the last delimiter position in the line, ignoring inline comments.
     * Inline comments (-- comment) are considered part of the line but delimiter
     * should be before them. We need to check if -- is actually a comment (not in a string).
     * 
     * @param line the trimmed line
     * @return the position of the last delimiter, or -1 if not found
     */
    private int findLastDelimiterPosition(String line) {
        String delimiter = COMMON.DEFAUT_DELIMITER;
        
        // First, find the position where inline comment starts (if any)
        // We need to check if -- is actually a comment (not in a string)
        int commentStart = -1;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        
        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);
            
            // Handle backslash escape
            if (c == '\\' && i + 1 < line.length()) {
                i++; // Skip the escaped character
                continue;
            }
            
            // Handle SQL-style escaped single quote ('')
            if (c == '\'' && inSingleQuote && i + 1 < line.length() && line.charAt(i + 1) == '\'') {
                i++; // Skip the second quote
                continue;
            }
            
            // Handle single quote
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            
            // Handle double quote
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            
            // Check for inline comment (-- comment) - only if not in a string
            if (c == '-' && line.charAt(i + 1) == '-' && !inSingleQuote && !inDoubleQuote) {
                commentStart = i;
                break;
            }
        }
        
        // Search for delimiter from end, but stop at comment start
        int searchEnd = commentStart == -1 ? line.length() : commentStart;
        for (int i = searchEnd - delimiter.length(); i >= 0; i--) {
            String substr = line.substring(i, i + delimiter.length());
            if (substr.equals(delimiter)) {
                return i;
            }
        }
        
        return -1;
    }
    
    /**
     * Check if a position is inside a string literal (single or double quoted).
     * This method correctly handles:
     * - Escaped quotes: \' and \"
     * - SQL-style escaped single quotes: ''
     * - Overlapping quotes: '`~"\''\\'
     * - Multi-line strings: tracks state from accumulated command
     * 
     * @param accumulatedCommand the accumulated command from previous lines
     * @param currentLine the current line to check
     * @param position the position in current line to check (should be the delimiter position)
     * @return true if position is inside a string literal, false otherwise
     */
    private boolean isInsideStringLiteral(String accumulatedCommand, String currentLine, int position) {
        // First, track string state through accumulated command
        StringState state = trackStringState(accumulatedCommand);
        
        // Then, continue tracking through current line up to delimiter position
        for (int i = 0; i < position; i++) {
            char c = currentLine.charAt(i);
            
            // Handle SQL-style escaped single quote ('') first - only valid inside single quote string
            // This must be checked before backslash escape to correctly handle cases like '\''
            if (c == '\'' && state.inSingleQuote && i + 1 < currentLine.length() && 
                currentLine.charAt(i + 1) == '\'') {
                i++; // Skip the second quote
                continue;
            }
            
            // Handle backslash escape - only valid inside the corresponding quote type
            // Check this after SQL-style escape to avoid interfering with '' pattern
            if (c == '\\' && i + 1 < currentLine.length() && 
                (state.inSingleQuote || state.inDoubleQuote)) {
                i++; // Skip the escaped character
                continue;
            }
            
            // Handle single quote - only toggle if not in double quote string
            if (c == '\'' && !state.inDoubleQuote) {
                state.inSingleQuote = !state.inSingleQuote;
                continue;
            }
            
            // Handle double quote - only toggle if not in single quote string
            if (c == '"' && !state.inSingleQuote) {
                state.inDoubleQuote = !state.inDoubleQuote;
                continue;
            }
        }
        
        return state.inSingleQuote || state.inDoubleQuote;
    }
    
    /**
     * Track string literal state through a text segment.
     * Returns the final state (whether we're inside single or double quoted string).
     * 
     * @param text the text to track through
     * @return StringState object containing the final state
     */
    private StringState trackStringState(String text) {
        StringState state = new StringState();
        if (text == null || text.isEmpty()) {
            return state;
        }
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // Handle SQL-style escaped single quote ('') first - only valid inside single quote string
            // This must be checked before backslash escape to correctly handle cases like '\''
            if (c == '\'' && state.inSingleQuote && i + 1 < text.length() && 
                text.charAt(i + 1) == '\'') {
                i++; // Skip the second quote
                continue;
            }
            
            // Handle backslash escape - only valid inside the corresponding quote type
            // Check this after SQL-style escape to avoid interfering with '' pattern
            if (c == '\\' && i + 1 < text.length() && 
                (state.inSingleQuote || state.inDoubleQuote)) {
                i++; // Skip the escaped character
                continue;
            }
            
            // Handle single quote - only toggle if not in double quote string
            if (c == '\'' && !state.inDoubleQuote) {
                state.inSingleQuote = !state.inSingleQuote;
                continue;
            }
            
            // Handle double quote - only toggle if not in single quote string
            if (c == '"' && !state.inSingleQuote) {
                state.inDoubleQuote = !state.inDoubleQuote;
                continue;
            }
        }
        
        return state;
    }
    
    /**
     * Helper class to track string literal state.
     */
    private static class StringState {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
    }

    public TestScript getTestScript(){
        return testScript;
    }

    public static void main(String[] args){
       
    }
}

