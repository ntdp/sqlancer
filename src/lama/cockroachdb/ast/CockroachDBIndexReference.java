package lama.cockroachdb.ast;

import lama.Randomly;
import lama.cockroachdb.CockroachDBSchema.CockroachDBIndex;
import lama.visitor.UnaryOperation;

public class CockroachDBIndexReference extends CockroachDBTableReference implements UnaryOperation<CockroachDBExpression> {
	
	private CockroachDBTableReference tableReference;
	private CockroachDBIndex index;

	public CockroachDBIndexReference(CockroachDBTableReference tableReference, CockroachDBIndex index) {
		super(tableReference.getTable());
		this.tableReference = tableReference;
		this.index = index;
	}

	@Override
	public CockroachDBExpression getExpression() {
		return tableReference;
	}

	@Override
	public String getOperatorRepresentation() {
		if (Randomly.getBoolean()) {
			return String.format("@{FORCE_INDEX=%s}", index.getIndexName());
		} else {
			return String.format("@{FORCE_INDEX=%s,%s}", index.getIndexName(), Randomly.fromOptions("ASC", "DESC"));
		}
	}

	@Override
	public OperatorKind getOperatorKind() {
		return OperatorKind.POSTFIX;
	}
	
	@Override
	public boolean omitBracketsWhenPrinting() {
		return true;
	}
	
}
