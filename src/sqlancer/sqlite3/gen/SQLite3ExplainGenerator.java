package sqlancer.sqlite3.gen;

import java.sql.SQLException;

import sqlancer.Query;
import sqlancer.QueryAdapter;
import sqlancer.Randomly;
import sqlancer.sqlite3.SQLite3Provider;
import sqlancer.sqlite3.SQLite3Provider.Action;
import sqlancer.sqlite3.SQLite3Provider.SQLite3GlobalState;

public class SQLite3ExplainGenerator {

    public static Query explain(SQLite3GlobalState globalState) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("EXPLAIN ");
        if (Randomly.getBoolean()) {
            sb.append("QUERY PLAN ");
        }
        Action action;
        do {
            action = Randomly.fromOptions(SQLite3Provider.Action.values());
        } while (action == Action.EXPLAIN);
        Query query = action.getQuery(globalState);
        sb.append(query);
        return new QueryAdapter(sb.toString(), query.getExpectedErrors());
    }

}
