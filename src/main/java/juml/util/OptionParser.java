// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import java.util.*;

/**
 * コマンド引数をオプション解析するkクラス
 */
public class OptionParser {
    private final Option[] options;
    private final LinkedList<String> arguments;

    public OptionParser(Option[] options){
        this.options = options;
        arguments = new LinkedList<String>();
    }

    public void parse(String[] args)
            throws UnknownOptionException, MissingArgumentException {
        parse(args, 0, args.length);
    }

    public void parse(String[] args, int index)
            throws UnknownOptionException, MissingArgumentException {
        parse(args, index, args.length - index);
    }

    public void parse(String[] args, int index, int count)
            throws UnknownOptionException, MissingArgumentException {
        Map<String, Option> shortOpts = new HashMap<String, Option>();
        Map<String, Option> longOpts = new HashMap<String, Option>();

        arguments.clear();

        for(Option opt : options){
            if(opt.getShortOption() != null){
                shortOpts.put(opt.getShortOption(), opt);
            }
            if(opt.getLongOption() != null){
                longOpts.put(opt.getLongOption(), opt);
            }
        }

        boolean raw = false;
        Option opt = null;
        for(int i=index; i-index<count && i < args.length; ++i){
            String arg = args[i];
            if(opt != null && !raw){
                // 値待ちのオプションがあるのに、次トークンが「登録済みの別オプション」なら
                // 値の指定漏れとみなしてエラーにする。別フラグを値として飲み込むと、
                // 後続オプションが無効化されて黙って誤動作するため。
                // (登録に無い "-1" のような負数値は通常の値として受け付ける。)
                if(isKnownOption(arg, shortOpts, longOpts)){
                    throw new MissingArgumentException(optName(opt));
                }
                opt.getArguments().add(arg);
                opt.setSet(true);
                opt = null;
                continue;
            }
            if( !raw && arg.equals("--") ){
                raw = true;
            }
            else if(raw){
                arguments.add(arg);
            }
            else if(arg.startsWith("--")){
                // 長いオプション。--opt=val 形式は '=' で分割する。
                String body = arg.substring(2);
                int eq = body.indexOf('=');
                String key = eq < 0 ? body : body.substring(0, eq);
                Option o = longOpts.get(key);
                if(o == null) throw new UnknownOptionException(arg);
                opt = applyOption(o, eq < 0 ? null : body.substring(eq + 1));
            }
            else if(arg.startsWith("-") && arg.length() > 1){
                // 短いオプション。-x=val 形式も許す。
                String body = arg.substring(1);
                int eq = body.indexOf('=');
                String key = eq < 0 ? body : body.substring(0, eq);
                Option o = shortOpts.get(key);
                if(o == null) throw new UnknownOptionException(arg);
                opt = applyOption(o, eq < 0 ? null : body.substring(eq + 1));
            }
            else{
                arguments.add(arg);
            }
        }
        // 末尾で値待ちのまま引数が尽きた (例: 末尾の `-o` に値なし)。以前は無言で
        // 握り潰され、値必須オプションが無効のまま処理が進んでいた。
        if(opt != null){
            throw new MissingArgumentException(optName(opt));
        }
    }

    /**
     * オプションを適用する。{@code inlineValue} が非 null なら {@code --opt=val} 形式の値として
     * 即セットし、null かつ値必須なら「次トークンが値」を表すためそのオプションを返す。
     */
    private static Option applyOption(Option o, String inlineValue){
        if(!o.isRequireArgument()){
            o.setSet(true);
            return null;
        }
        if(inlineValue != null){
            o.getArguments().add(inlineValue);
            o.setSet(true);
            return null;
        }
        return o; // 次トークンを値として待つ
    }

    /** {@code arg} が登録済みの短い/長いオプションを指しているか (=val 形式も考慮)。 */
    private static boolean isKnownOption(String arg,
            Map<String, Option> shortOpts, Map<String, Option> longOpts){
        if(arg.equals("--") || arg.length() < 2 || arg.charAt(0) != '-'){
            return false;
        }
        String body = arg.startsWith("--") ? arg.substring(2) : arg.substring(1);
        int eq = body.indexOf('=');
        String key = eq < 0 ? body : body.substring(0, eq);
        return arg.startsWith("--") ? longOpts.containsKey(key) : shortOpts.containsKey(key);
    }

    /** 例外表示用のオプション名 (長い名 > 短い名)。 */
    private static String optName(Option o){
        if(o.getLongOption() != null){
            return "--" + o.getLongOption();
        }
        if(o.getShortOption() != null){
            return "-" + o.getShortOption();
        }
        return "?";
    }

    public LinkedList<String> getArguments(){
        return arguments;
    }
}
