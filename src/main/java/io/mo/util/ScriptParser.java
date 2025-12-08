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
     *   <li>Find the last delimiter position, ignoring inline comments</li>
     *   <li>Track string state from accumulated command through current line to delimiter position</li>
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
        
        String delimiter = COMMON.DEFAUT_DELIMITER;
        
        // Step 1: Track string state from accumulated command through current line
        // This unified tracking will help us find both comments and delimiter correctly
        StringState state = trackStringState(accumulatedCommand);
        int commentStart = -1;
        
        // Track state through current line and find comment start
        for (int i = 0; i < trimmed.length(); i++) {
            int newPos = processChar(trimmed, i, state);
            
            // Check for inline comment (-- comment) - only if not in a string
            if (commentStart == -1 && newPos < trimmed.length() - 1 && 
                trimmed.charAt(newPos) == '-' && trimmed.charAt(newPos + 1) == '-' && 
                !state.inSingleQuote && !state.inDoubleQuote) {
                commentStart = newPos;
            }
            
            i = newPos;
        }
        
        // Step 2: Search for delimiter from end, but stop at comment start
        int searchEnd = commentStart == -1 ? trimmed.length() : commentStart;
        int delimiterPos = -1;
        for (int i = searchEnd - delimiter.length(); i >= 0; i--) {
            if (i + delimiter.length() <= trimmed.length()) {
                String substr = trimmed.substring(i, i + delimiter.length());
                if (substr.equals(delimiter)) {
                    delimiterPos = i;
                    break;
                }
            }
        }
        
        if (delimiterPos == -1) {
            return false;
        }
        
        // Step 3: Track string state from accumulated command through current line to delimiter position
        // We need to re-track because we need the state at delimiter position, not at end of line
        state = trackStringState(accumulatedCommand);
        for (int i = 0; i < delimiterPos; i++) {
            i = processChar(trimmed, i, state);
        }
        
        // Step 4: Check if delimiter is inside any string literal
        return !state.inSingleQuote && !state.inDoubleQuote;
    }
    
    /**
     * Process a character and update string state accordingly.
     * Handles:
     * - Backslash escapes: \', \", \\
     * - SQL-style escaped single quotes: ''
     * - Special case: \'' in single-quoted strings (needs context-aware handling)
     * - Quote toggling: ', "
     * 
     * @param text the text being processed
     * @param pos the current position
     * @param state the current string state
     * @return the new position (may be advanced if escape sequences are processed)
     */
    private int processChar(String text, int pos, StringState state) {
        if (pos >= text.length()) {
            return pos;
        }
        
        char c = text.charAt(pos);
        
        // Handle backslash escape - only valid inside the corresponding quote type
        if (c == '\\' && pos + 1 < text.length() && 
            (state.inSingleQuote || state.inDoubleQuote)) {
            char nextChar = text.charAt(pos + 1);
            
            // Special case: \'' in single-quoted string
            // When we see \'', we need to determine if the second quote is part of SQL-style '' escape
            // or if it's the string terminator.
            // Strategy: Only treat \'' specially if it's clearly in the middle of string content.
            // If \'' is followed by SQL syntax terminators (like ), ;, or , at end), it's string end.
            if (state.inSingleQuote && nextChar == '\'' && pos + 2 < text.length() && 
                text.charAt(pos + 2) == '\'') {
                // Check if \'' is followed by more string content (not SQL syntax)
                boolean hasMoreStringContent = false;
                if (pos + 3 < text.length()) {
                    char afterSecondQuote = text.charAt(pos + 3);
                    // If followed by ), ;, it's definitely string end
                    if (afterSecondQuote == ')' || afterSecondQuote == ';') {
                        hasMoreStringContent = false;
                    } else if (afterSecondQuote == ',') {
                        // Comma: in CREATE TABLE, comma at end typically ends the value
                        // Check if there's more non-whitespace content after comma on the SAME line
                        // We need to check if comma is followed by newline (end of line) or more content
                        int j = pos + 4;
                        while (j < text.length() && Character.isWhitespace(text.charAt(j)) && 
                               text.charAt(j) != '\n' && text.charAt(j) != '\r') {
                            j++;
                        }
                        // Only if comma is followed by non-whitespace on the same line (not newline), it's more content
                        hasMoreStringContent = (j < text.length() && text.charAt(j) != '\n' && text.charAt(j) != '\r');
                    } else {
                        // Other characters indicate more string content
                        hasMoreStringContent = true;
                    }
                }
                // else: no more characters, so \'' is at end, don't special handle
                
                if (hasMoreStringContent) {
                    // There's string content after \'', so treat it as: \' (escaped quote) + ' (part of SQL-style '')
                    // Skip \' and first ' of ''
                    return pos + 2;
                }
                // Otherwise, treat \' normally (skip escaped quote), let the '' be handled by SQL-style escape logic
            }
            // Normal backslash escape: skip the escaped character
            return pos + 1;
        }
        
        // Handle SQL-style escaped single quote ('') - only valid inside single quote string
        // This must be checked before single quote toggle to avoid interfering
        if (c == '\'' && state.inSingleQuote && pos + 1 < text.length() && 
            text.charAt(pos + 1) == '\'') {
            // Skip the second quote
            return pos + 1;
        }
        
        // Handle single quote - only toggle if not in double quote string
        if (c == '\'' && !state.inDoubleQuote) {
            state.inSingleQuote = !state.inSingleQuote;
            return pos;
        }
        
        // Handle double quote - only toggle if not in single quote string
        if (c == '"' && !state.inSingleQuote) {
            state.inDoubleQuote = !state.inDoubleQuote;
            return pos;
        }
        
        return pos;
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
            i = processChar(text, i, state);
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

