package org.jasm.item.instructions.verify.types;


public class DoubleType extends VerificationType {
	
	public DoubleType() {

	}

	@Override
	protected boolean isAssignableFromDouble(DoubleType from) {
		return true;
	}

	@Override
	protected boolean isAssignableFromFloat(FloatType from) {
		return false;
	}

	@Override
	protected boolean isAssignableFromLong(LongType from) {
		return false;
	}

	@Override
	protected boolean isAssignableFromNull(NullType from) {
		return false;
	}

	@Override
	protected boolean isAssignableFromObjectValue(ObjectValueType from) {
		return false;
	}

	@Override
	protected boolean isAssignableFromTop(TopType from) {
		return false;
	}

	@Override
	protected boolean isAssignableFromUninitializedThis(
			UninitializedThisType from) {
		return false;
	}

	@Override
	protected boolean isAssignableFromUninitializedValue(
			UninitializedValueType from) {
		return false;
	}

	@Override
	protected VerificationType mergeWithDouble(DoubleType from) {
		return this;
	}

	@Override
	protected VerificationType mergeWithFloat(FloatType from) {
		return TOP;
	}

	@Override
	protected VerificationType mergeWithLong(LongType from) {
		return TOP;
	}

	@Override
	protected VerificationType mergeWithNull(NullType from) {
		return TOP;
	}

	@Override
	protected VerificationType mergeWithObjectValue(ObjectValueType from) {
		return TOP;
	}

	@Override
	protected VerificationType mergeWithTop(TopType from) {
		return TOP;
	}

	@Override
	protected VerificationType mergeWithUninitializedThis(
			UninitializedThisType from) {
		return TOP;
	}

	@Override
	protected VerificationType mergeWithUninitializedValue(
			UninitializedValueType from) {
		return TOP;
	}

	@Override
	protected boolean isAssignableFromInt(IntType from) {
		return false;
	}

	@Override
	protected VerificationType mergeWithInt(IntType from) {
		return TOP;
	}

	@Override
	public int getSize() {
		return 2;
	}
	
	@Override
	public String toString() {
		return "double";
	}

}
