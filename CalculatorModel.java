import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 電卓の計算状態と計算処理を管理するモデルクラス。
 */
public class CalculatorModel {

    // 入力可能な最大桁数
    private static final int MAX_INPUT_LENGTH = 8;

    // 表示・入力で使用する固定文字列
    private static final String ERROR_TEXT = "エラー";
    private static final String ZERO = "0";
    private static final String NEGATIVE_ZERO = "-0";
    private static final String ZERO_WITH_DOT = "0.";
    private static final String NEGATIVE_ZERO_WITH_DOT = "-0.";
    private static final String NEGATIVE_SIGN = "-";
    private static final String DOT = ".";
    private static final String NEGATIVE_DOT = "-.";

    // 左辺の値
    private BigDecimal leftOperand;

    // 現在入力中の値
    private final StringBuilder currentInput = new StringBuilder();

    // 画面表示用の式
    private final StringBuilder expression = new StringBuilder();

    // 保持中の演算子
    private Operator pendingOp;

    // 現在の入力状態
    private InputState state;

    /**
     * コンストラクタ。
     * 電卓を初期状態にする。
     */
    public CalculatorModel() {
        clearAll();
    }

    /**
     * 電卓の状態をすべて初期化する。
     * 表示は 0 に戻す。
     */
    public void clearAll() {
        leftOperand = null;
        pendingOp = null;
        currentInput.setLength(0);
        expression.setLength(0);
        expression.append(ZERO);
        state = InputState.READY;
    }

    /**
     * 表示が空か、0のみかを判定する。
     *
     * @return 空または0なら true
     */
    public boolean isEmpty() {
        return expression.length() == 0 || ZERO.contentEquals(expression);
    }

    /**
     * 画面に表示する文字列を返す。
     *
     * @return 表示文字列
     */
    public String getDisplayText() {
        if (state == InputState.ERROR) {
            return ERROR_TEXT;
        }

        if (expression.length() == 0) {
            return ZERO;
        }

        return expression.toString();
    }

    /**
     * 表示文字列を返す。
     *
     * @return 表示文字列
     */
    public String getText() {
        return getDisplayText();
    }

    /**
     * 何も入力されていない状態で演算子が押されたとき、
     * 左辺を 0 として計算を開始する。
     *
     * @param op 入力された演算子
     */
    public void startWithZero(Operator op) {
        if (op == null) {
            return;
        }

        expression.setLength(0);

        if (op == Operator.SUB) {
            expression.append(ZERO).append(operatorSymbol(op));
            leftOperand = BigDecimal.ZERO;
            pendingOp = Operator.SUB;
        } else {
            expression.append(ZERO).append(operatorSymbol(op));
            leftOperand = BigDecimal.ZERO;
            pendingOp = op;
        }

        currentInput.setLength(0);
        state = InputState.INPUT_OPERATOR;
    }

    /**
     * 数字を現在入力中の値の末尾に追加する。
     *
     * @param ch 入力された数字
     * @return 追加できた場合は true
     */
    public boolean appendDigit(char ch) {
        if (!Character.isDigit(ch)) {
            return false;
        }

        if (state == InputState.ERROR) {
            return false;
        }

        // 計算結果表示後に数字が押されたら新しい入力を開始する
        if (state == InputState.READY && leftOperand != null && pendingOp == null
                && currentInput.length() == 0) {
            clearAll();
        }

        if (getEffectiveInputLength() >= MAX_INPUT_LENGTH) {
            return false;
        }

        // 先頭が 0 の場合は押された数字で置き換える
        if (currentInput.length() == 1 && currentInput.charAt(0) == '0') {
            currentInput.setLength(0);
            currentInput.append(ch);
            syncExpressionWithCurrentInput();
            state = InputState.INPUT_NUMBER;
            return true;
        }

        if (NEGATIVE_ZERO.equals(currentInput.toString())) {
            return false;
        }

        currentInput.append(ch);

        if (ZERO.contentEquals(expression)) {
            expression.setLength(0);
        }

        expression.append(ch);
        state = InputState.INPUT_NUMBER;
        return true;
    }

    /**
     * 小数点を追加する。
     *
     * @return 追加できた場合は true
     */
    public boolean appendDot() {
        if (state == InputState.ERROR) {
            return false;
        }

        // 計算結果表示後に . が押されたら新しい入力を開始する
        if (state == InputState.READY && leftOperand != null && pendingOp == null
                && currentInput.length() == 0) {
            clearAll();
        }

        if (currentInput.indexOf(DOT) >= 0) {
            return false;
        }

        if (NEGATIVE_SIGN.equals(currentInput.toString())) {
            return false;
        }

        if (getEffectiveInputLength() >= MAX_INPUT_LENGTH) {
            return false;
        }

        // 入力が空なら 0. から開始する
        if (currentInput.length() == 0) {
            currentInput.append(ZERO_WITH_DOT);

            if (ZERO.contentEquals(expression)) {
                expression.setLength(0);
            }

            expression.append(ZERO_WITH_DOT);
            state = InputState.INPUT_NUMBER;
            return true;
        }

        currentInput.append(DOT);
        expression.append(DOT);
        state = InputState.INPUT_NUMBER;
        return true;
    }

    /**
     * 演算子を設定する。
     *
     * @param op 入力された演算子
     */
    public void setOperator(Operator op) {
        inputOperator(op);
    }

    /**
     * 演算子入力を処理する。
     *
     * @param op 入力された演算子
     */
    public void inputOperator(Operator op) {
        if (state == InputState.ERROR || op == null) {
            return;
        }

        // 初期状態では - のみ負数入力として受け付ける
        if (state == InputState.READY && leftOperand == null && currentInput.length() == 0) {
            if (op == Operator.SUB) {
                startNegativeNumber();
            }
            return;
        }

        // 計算結果表示後は結果を左辺として演算子を受け付ける
        if (state == InputState.READY && leftOperand != null && pendingOp == null
                && currentInput.length() == 0) {
            expression.setLength(0);
            expression.append(FormatterUtil.formatResultForDisplay(leftOperand));
            appendOrReplaceOperator(op);
            pendingOp = op;
            state = InputState.INPUT_OPERATOR;
            return;
        }

        // 演算子が連続で押されたら最後の演算子を上書きする
        if (state == InputState.INPUT_OPERATOR) {
            appendOrReplaceOperator(op);
            pendingOp = op;
            return;
        }

        // 0 の直後は +, ×, ÷ を無効にし、- だけ負号として扱う
        if (state == InputState.INPUT_NUMBER && ZERO.equals(currentInput.toString())) {
            if (op == Operator.SUB) {
                startNegativeNumber();
            }
            return;
        }

        if (isInvalidOperandInput()) {
            return;
        }

        BigDecimal currentValue = safeParse(currentInput.toString());
        if (currentValue == null) {
            return;
        }

        if (leftOperand == null) {
            leftOperand = currentValue;
        } else if (pendingOp != null) {
            BigDecimal applied = apply(leftOperand, currentValue, pendingOp);
            if (state == InputState.ERROR) {
                return;
            }
            leftOperand = applied;
        }

        pendingOp = op;
        currentInput.setLength(0);
        expression.setLength(0);
        expression.append(FormatterUtil.formatResultForDisplay(leftOperand));
        appendOrReplaceOperator(op);
        state = InputState.INPUT_OPERATOR;
    }

    /**
     * イコール操作を実行する。
     */
    public void equalsOp() {
        try {
            evaluate();
        } catch (Exception e) {
            ErrorHandler.handle(e);
            onError();
        }
    }

    /**
     * 左辺・右辺・演算子を使って計算を実行する。
     */
    public void evaluate() {
        if (state == InputState.ERROR) {
            return;
        }

        if (pendingOp == null || leftOperand == null || currentInput.length() == 0) {
            return;
        }

        BigDecimal right = safeParse(currentInput.toString());
        if (right == null) {
            return;
        }

        BigDecimal result = apply(leftOperand, right, pendingOp);
        if (state == InputState.ERROR) {
            return;
        }

        leftOperand = result;
        pendingOp = null;
        currentInput.setLength(0);
        expression.setLength(0);
        expression.append(FormatterUtil.formatResultForDisplay(result));
        state = InputState.READY;
    }

    // 負数入力を開始する
    private void startNegativeNumber() {
        currentInput.setLength(0);
        currentInput.append(NEGATIVE_SIGN);
        expression.setLength(0);
        expression.append(NEGATIVE_SIGN);
        state = InputState.INPUT_NUMBER;
    }

    // 演算子を受け付けられない入力状態かを判定する
    private boolean isInvalidOperandInput() {
        String text = currentInput.toString();
        return text.isEmpty()
                || ZERO.equals(text)
                || ZERO_WITH_DOT.equals(text)
                || NEGATIVE_ZERO.equals(text)
                || NEGATIVE_ZERO_WITH_DOT.equals(text)
                || NEGATIVE_SIGN.equals(text)
                || DOT.equals(text)
                || NEGATIVE_DOT.equals(text);
    }

    // 2項演算を実行する
    private BigDecimal apply(BigDecimal left, BigDecimal right, Operator op) {
        try {
            switch (op) {
                case ADD:
                    return left.add(right);
                case SUB:
                    return left.subtract(right);
                case MUL:
                    return left.multiply(right);
                case DIV:
                    if (right.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ArithmeticException("0で割ることはできません");
                    }
                    return left.divide(right, 16, RoundingMode.HALF_UP).stripTrailingZeros();
                default:
                    return right;
            }
        } catch (Exception e) {
            ErrorHandler.handle(e);
            onError();
            return BigDecimal.ZERO;
        }
    }

    // 文字列を BigDecimal に変換する
    private BigDecimal safeParse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        if (NEGATIVE_SIGN.equals(text) || DOT.equals(text) || NEGATIVE_DOT.equals(text)) {
            return null;
        }

        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 入力中の数値に含まれる数字の個数を返す
    // 小数点と負号は桁数に含めない
    private int getEffectiveInputLength() {
        int count = 0;

        for (int i = 0; i < currentInput.length(); i++) {
            char ch = currentInput.charAt(i);
            if (Character.isDigit(ch)) {
                count++;
            }
        }

        return count;
    }

    // currentInput の内容に合わせて expression を同期する
    private void syncExpressionWithCurrentInput() {
        expression.setLength(0);

        if (leftOperand != null && pendingOp != null) {
            expression.append(FormatterUtil.formatResultForDisplay(leftOperand));
            expression.append(operatorSymbol(pendingOp));
            expression.append(currentInput);
            return;
        }

        expression.append(currentInput);
    }

    // 式の末尾に演算子を追加する
    // すでに演算子がある場合は新しい演算子で上書きする
    private void appendOrReplaceOperator(Operator op) {
        char symbol = operatorSymbol(op);

        if (expression.length() == 0) {
            expression.append(symbol);
            return;
        }

        int lastIndex = expression.length() - 1;
        char lastChar = expression.charAt(lastIndex);

        if (isOperatorChar(lastChar)) {
            expression.setCharAt(lastIndex, symbol);
            return;
        }

        expression.append(symbol);
    }

    // 文字が演算子記号かどうかを判定する
    private boolean isOperatorChar(char ch) {
        return ch == '+' || ch == '-' || ch == '×' || ch == '÷';
    }

    // 演算子を表示用記号に変換する
    private char operatorSymbol(Operator op) {
        switch (op) {
            case ADD:
                return '+';
            case SUB:
                return '-';
            case MUL:
                return '×';
            case DIV:
                return '÷';
            default:
                return '?';
        }
    }

    // エラー状態にする
    private void onError() {
        state = InputState.ERROR;
        leftOperand = null;
        pendingOp = null;
        currentInput.setLength(0);
        expression.setLength(0);
        expression.append(ERROR_TEXT);
    }
}
