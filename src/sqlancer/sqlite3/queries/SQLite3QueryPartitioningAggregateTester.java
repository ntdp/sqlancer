package sqlancer.sqlite3.queries;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sqlancer.DatabaseProvider;
import sqlancer.IgnoreMeException;
import sqlancer.QueryAdapter;
import sqlancer.Randomly;
import sqlancer.TestOracle;
import sqlancer.sqlite3.SQLite3Errors;
import sqlancer.sqlite3.SQLite3Provider.SQLite3GlobalState;
import sqlancer.sqlite3.SQLite3Visitor;
import sqlancer.sqlite3.ast.SQLite3Aggregate;
import sqlancer.sqlite3.ast.SQLite3Aggregate.SQLite3AggregateFunction;
import sqlancer.sqlite3.ast.SQLite3Expression;
import sqlancer.sqlite3.ast.SQLite3Expression.SQLite3PostfixText;
import sqlancer.sqlite3.ast.SQLite3Expression.SQLite3PostfixUnaryOperation;
import sqlancer.sqlite3.ast.SQLite3Expression.SQLite3PostfixUnaryOperation.PostfixUnaryOperator;
import sqlancer.sqlite3.ast.SQLite3Select;
import sqlancer.sqlite3.ast.SQLite3UnaryOperation;
import sqlancer.sqlite3.ast.SQLite3UnaryOperation.UnaryOperator;
import sqlancer.sqlite3.gen.SQLite3Common;
import sqlancer.sqlite3.gen.SQLite3ExpressionGenerator;
import sqlancer.sqlite3.schema.SQLite3Schema;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Tables;

public class SQLite3QueryPartitioningAggregateTester implements TestOracle {

    private SQLite3GlobalState state;
    private final List<String> errors = new ArrayList<>();
    private SQLite3ExpressionGenerator gen;

    public SQLite3QueryPartitioningAggregateTester(SQLite3GlobalState state) {
        this.state = state;
        SQLite3Errors.addExpectedExpressionErrors(errors);
    }

    @Override
    public void check() throws SQLException {
        SQLite3Schema s = state.getSchema();
        SQLite3Tables targetTables = s.getRandomTableNonEmptyTables();
        gen = new SQLite3ExpressionGenerator(state).setColumns(targetTables.getColumns());
        SQLite3Select select = new SQLite3Select();
        SQLite3AggregateFunction windowFunction = Randomly.fromOptions(SQLite3Aggregate.SQLite3AggregateFunction.MIN,
                SQLite3Aggregate.SQLite3AggregateFunction.MAX, SQLite3AggregateFunction.SUM,
                SQLite3AggregateFunction.TOTAL);
        SQLite3Aggregate aggregate = new SQLite3Aggregate(gen.getRandomExpressions(1), windowFunction);
        select.setFetchColumns(Arrays.asList(aggregate));
        List<SQLite3Expression> from = SQLite3Common.getTableRefs(targetTables.getTables(), s);
        select.setFromList(from);
        if (Randomly.getBoolean()) {
            select.setOrderByExpressions(gen.generateOrderBys());
        }
        String originalQuery = SQLite3Visitor.asString(select);

        SQLite3Expression whereClause = gen.generateExpression();
        SQLite3UnaryOperation negatedClause = new SQLite3UnaryOperation(UnaryOperator.NOT, whereClause);
        SQLite3PostfixUnaryOperation notNullClause = new SQLite3PostfixUnaryOperation(PostfixUnaryOperator.ISNULL,
                whereClause);

        SQLite3Select leftSelect = getSelect(aggregate, from, whereClause);
        SQLite3Select middleSelect = getSelect(aggregate, from, negatedClause);
        SQLite3Select rightSelect = getSelect(aggregate, from, notNullClause);
        String metamorphicText = "SELECT " + aggregate.getFunc().toString() + "(aggr) FROM (";
        metamorphicText += SQLite3Visitor.asString(leftSelect) + " UNION ALL " + SQLite3Visitor.asString(middleSelect)
                + " UNION ALL " + SQLite3Visitor.asString(rightSelect);
        metamorphicText += ")";

        // String finalText = originalQuery + " INTERSECT " + metamorphicText;
        // state.getState().queryString = "--" + finalText;
        String firstResult;
        String secondResult;
        QueryAdapter q = new QueryAdapter(originalQuery, errors);
        try (ResultSet result = q.executeAndGet(state.getConnection())) {
            if (result == null) {
                throw new IgnoreMeException();
            }
            firstResult = result.getString(1);
        } catch (Exception e) {
            // TODO
            throw new IgnoreMeException();
        }

        QueryAdapter q2 = new QueryAdapter(metamorphicText, errors);
        try (ResultSet result = q2.executeAndGet(state.getConnection())) {
            if (result == null) {
                throw new IgnoreMeException();
            }
            secondResult = result.getString(1);
        } catch (Exception e) {
            // TODO
            throw new IgnoreMeException();
        }
        state.getState().queryString = "--" + originalQuery + "\n--" + metamorphicText + "\n-- " + firstResult + "\n-- "
                + secondResult;
        if (firstResult == null && secondResult != null
                || firstResult != null && !firstResult.contentEquals(secondResult)) {

            if (!DatabaseProvider.isEqualDouble(firstResult, secondResult)) {
                throw new AssertionError();
            }

        }

    }

    private SQLite3Select getSelect(SQLite3Aggregate aggregate, List<SQLite3Expression> from,
            SQLite3Expression whereClause) {
        SQLite3Select leftSelect = new SQLite3Select();
        leftSelect.setFetchColumns(Arrays.asList(new SQLite3PostfixText(aggregate, " as aggr", null)));
        leftSelect.setFromList(from);
        leftSelect.setWhereClause(whereClause);
        if (Randomly.getBooleanWithRatherLowProbability()) {
            leftSelect.setGroupByClause(gen.getRandomExpressions(Randomly.smallNumber() + 1));
        }
        if (Randomly.getBoolean()) {
            leftSelect.setOrderByExpressions(gen.generateOrderBys());
        }
        return leftSelect;
    }

}
