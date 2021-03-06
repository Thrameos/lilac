package org.jasm.item.instructions.verify.types;


public class OneWordType extends VerificationType {
	
	public OneWordType() {
		
	}

	@Override
	protected boolean isAssignableFromDouble(DoubleType from) {
		return false;
	}

	@Override
	protected boolean isAssignableFromFloat(FloatType from) {
		return true;
	}

	@Override
	protected boolean isAssignableFromInt(IntType from) {
		return true;
	}

	@Override
	protected boolean isAssignableFromLong(LongType from) {
		return false;
	}

	@Override
	protected boolean isAssignableFromNull(NullType from) {
		return true;
	}

	@Override
	protected boolean isAssignableFromObjectValue(ObjectValueType from) {
		return true;
	}

	@Override
	protected boolean isAssignableFromTop(TopType from) {
		return false;
	}

	@Override
	protected boolean isAssignableFromUninitializedThis(
			UninitializedThisType from) {
		return true;
	}

	@Override
	protected boolean isAssignableFromUninitializedValue(
			UninitializedValueType from) {
		return true;
	}

	@Override
	protected VerificationType mergeWithDouble(DoubleType from) {
		throw new IllegalStateException("merge not supported");
	}

	@Override
	protected VerificationType mergeWithFloat(FloatType from) {
		throw new IllegalStateException("merge not supported");
	}

	@Override
	protected VerificationType mergeWithInt(IntType from) {
		throw new IllegalStateException("merge not supported");
	}

	@Override
	protected VerificationType mergeWithLong(LongType from) {
		throw new IllegalStateException("merge not supported");
	}

	@Override
	protected VerificationType mergeWithNull(NullType from) {
		throw new IllegalStateException("merge not supported");
	}

	@Override
	protected VerificationType mergeWithObjectValue(ObjectValueType from) {
		throw new IllegalStateException("merge not supported");
	}

	@Override
	protected VerificationType mergeWithTop(TopType from) {
		throw new IllegalStateException("merge not supported");
	}

	@Override
	protected VerificationType mergeWithUninitializedThis(
			UninitializedThisType from) {
		throw new IllegalStateException("merge not supported");
	}

	@Override
	protected VerificationType mergeWithUninitializedValue(
			UninitializedValueType from) {
		throw new IllegalStateException("merge not supported");
	}

	@Override
	public int getSize() {
		return 1;
	}
	
	@Override
	public String toString() {
		return "one word";
	}

}
