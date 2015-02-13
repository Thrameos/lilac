package org.jasm.item.instructions.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.jasm.item.instructions.verify.error.InconsistentStackSizeException;
import org.jasm.item.instructions.verify.error.StackOverflowException;
import org.jasm.item.instructions.verify.error.StackmapAppendOverflowException;
import org.jasm.item.instructions.verify.error.StackmapChopUnderflowException;
import org.jasm.item.instructions.verify.error.StackmapFullLocalsOverflowException;
import org.jasm.item.instructions.verify.error.StackmapFullStackOverflowException;
import org.jasm.item.instructions.verify.error.StackmapSameLocalsStackOverflowException;
import org.jasm.item.instructions.verify.error.UnexpectedRegisterTypeException;
import org.jasm.item.instructions.verify.error.UnexpectedStackTypeException;
import org.jasm.item.instructions.verify.types.IClassQuery;
import org.jasm.item.instructions.verify.types.ObjectValueType;
import org.jasm.item.instructions.verify.types.VerificationType;
import org.jasm.type.descriptor.MethodDescriptor;
import org.jasm.type.descriptor.TypeDescriptor;

public class Frame {
	
	private int maxStackSize;
	
	private List<VerificationType> locals;
	private Stack<VerificationType> stack;
	
	private int currentStackSize = 0;
	
	private int activeLocals = 0;
	
	public Frame(int maxLocals, int maxStackSize) {
		locals = new ArrayList<VerificationType>();
		stack = new Stack<VerificationType>();
		this.maxStackSize = maxStackSize;
		for (int i=0;i<maxLocals; i++) {
			locals.add(VerificationType.TOP);
		}
		this.activeLocals = calculateActiveLocals();
	}
	
	private Frame(List<VerificationType> locals, Stack<VerificationType> stack, int maxStackSize) {
		this.locals = new ArrayList<VerificationType>();
		this.locals.addAll(locals);
		this.stack = new Stack<VerificationType>();
		this.stack.addAll(stack);
		this.maxStackSize = maxStackSize;
		this.activeLocals = calculateActiveLocals();
		this.currentStackSize = 0;
		for (VerificationType t: stack) {
			this.currentStackSize+=t.getSize();
		}
		if (currentStackSize>maxStackSize) {
			throw new IllegalArgumentException(currentStackSize+">"+maxStackSize);
		}
	}
	

	public void push(VerificationType type) {
		if (currentStackSize+type.getSize() > maxStackSize) {
			throw new StackOverflowException(-1);
		}
		currentStackSize+=type.getSize();
		stack.push(type);
	}
	
	public VerificationType pop(VerificationType expected) {
		if (stack.size() == 0) {
			throw new StackOverflowException(-1);
		}
		VerificationType value = stack.peek();
		
		if (expected.isAssignableFrom(value)) {
			stack.pop();
			currentStackSize-=value.getSize();
			return value;
		} else {
			throw new UnexpectedStackTypeException(-1, stack.size()-1,expected, value);
		}
	}
	
	public VerificationType load(VerificationType expected, int register) {
		if (register<=0 || register>=locals.size()) {
			throw new IllegalArgumentException("illegal register index: "+register);
		}
		if ((expected.getSize() == 2 
				&& register>=locals.size()-1))  {
			throw new IllegalArgumentException("illegal register index for two word types: "+register);
		}
		
		VerificationType value = locals.get(register);
		if (!expected.isAssignableFrom(value)) {
			throw new UnexpectedRegisterTypeException(-1, register, expected, value);
		}
		push(value);
		return value;
	}
	
	public void store(VerificationType expected, int register) {
		if (register<=0 || register>=locals.size()) {
			throw new IllegalArgumentException("illegal register index: "+register);
		}
		if (expected.getSize() == 2
				&& register>=locals.size()-1)  {
			throw new IllegalArgumentException("illegal register index for two word types: "+register);
		}
		VerificationType value = pop(expected);
		locals.set(register, value);
		int activeLocalsCandidate = register+1;
		if (expected.getSize() == 2)  {
			locals.set(register+1, VerificationType.TOP);
			activeLocalsCandidate++;
		} else {
			if (register>0 && 
					(locals.get(register-1).equals(VerificationType.DOUBLE) || locals.get(register-1).equals(VerificationType.LONG))) {
				locals.set(register-1,VerificationType.TOP);
			}
		}
		
		activeLocals = Math.max(activeLocalsCandidate, activeLocals);
	}
	
	public boolean isAssignableFrom(Frame other) {
		if (other.locals.size() !=this.locals.size()) {
			throw new IllegalArgumentException("inconsistent locals sizes "+other.locals.size()+"!="+locals.size());
		}
		if (other.stack.size() != this.stack.size()) {
			throw new InconsistentStackSizeException(-1, other.stack.size(), this.stack.size());
		}
		
		for (int i=0;i<this.stack.size(); i++) {
			if (!this.stack.get(i).isAssignableFrom(other.stack.get(i))) {
				throw new UnexpectedStackTypeException(-1, i, this.stack.get(i), other.stack.get(i));
			}
		}
		
		for (int i=0;i<this.locals.size(); i++) {
			if (!this.locals.get(i).isAssignableFrom(other.locals.get(i))) {
				throw new UnexpectedRegisterTypeException(-1, i, this.locals.get(i), other.locals.get(i));
			}
		}
		
		return true;
	}
	
	public Frame merge(Frame other) {
		if (other.locals.size() !=this.locals.size()) {
			throw new IllegalArgumentException("inconsistent locals sizes "+other.locals.size()+"!="+locals.size());
		}
		if (other.stack.size() != this.stack.size()) {
			throw new InconsistentStackSizeException(-1, other.stack.size(), this.stack.size());
		}
		
		Stack newStack = new Stack();
		List newLocals = new ArrayList();
		
		for (int i=0;i<this.stack.size(); i++) {
			newStack.add(this.stack.get(i).mergeWith(other.stack.get(i)));
		}
		
		for (int i=0;i<this.locals.size(); i++) {
			newLocals.add(this.locals.get(i).mergeWith(other.locals.get(i)));
		}
		
		return new Frame(newLocals,newStack, maxStackSize);
	}
	
	public Frame copy() {
		return new Frame(locals,stack,maxStackSize);
	}



	public boolean same(Frame  other) {
		if (other.locals.size() !=this.locals.size()) {
			throw new IllegalArgumentException("inconsistent locals sizes "+other.locals.size()+"!="+locals.size());
		}
		if (other.stack.size() != this.stack.size()) {
			return false;
		}
		
		for (int i=0;i<this.stack.size(); i++) {
			if (!this.stack.get(i).equals(other.stack.get(i))) {
				return false;
			}
		}
		
		for (int i=0;i<this.locals.size(); i++) {
			if (!this.locals.get(i).isAssignableFrom(other.locals.get(i))) {
				return false;
			}
		}
		
		return true;
	}
	
	public int getCurrentStackSize() {
		return currentStackSize;
	}
	
	public int getActiveLocals() {
		return activeLocals;
	}

	public void replaceAllOccurences(VerificationType oldValue,VerificationType newValue) {
		for (int i=0;i<this.stack.size(); i++) {
			if (this.stack.get(i).equals(oldValue)) {
				this.stack.set(i,newValue);
			}
		}
		
		for (int i=0;i<this.locals.size(); i++) {
			if (this.locals.get(i).equals(oldValue)) {
				this.locals.set(i,newValue);
			}
		}
	}
	
	private int calculateActiveLocals() {
		int result = locals.size();
		while (result>0 && locals.get(result-1) == VerificationType.TOP) {
			result--;
		}
		if (result == 0) {
			return 0;
		} else {
			if (locals.get(result-1).getSize() == 2) {
				if (result == locals.size()) {
					throw new IllegalStateException("two words type in the last register");
				}
				result++;
			}
		}
		return result;
		
	}
	
	public static Frame createInitialFrame(String className, boolean isConstructor, boolean isStatic, int maxLocals,int maxStack,MethodDescriptor desc, IClassQuery classQuery) {
		if (isConstructor && isStatic) {
			throw new IllegalArgumentException("There are no static constructors");
		}
		List<VerificationType> locals = new ArrayList<VerificationType>();
		if (!isStatic) {
			if (isConstructor) {
				locals.add(VerificationType.UNINITIALIZED_THIS);
			} else {
				locals.add(new ObjectValueType(new TypeDescriptor("L"+className+";"), classQuery));
			}
		}
		for (TypeDescriptor typeDesc: desc.getParameters()) {
			VerificationType type = createTypeFromDescriptor(typeDesc, classQuery);
			locals.add(type);
			if (type.getSize()==2) {
				locals.add(VerificationType.TOP);
			}
		}
		if (maxLocals<locals.size()) {
			throw new IllegalArgumentException("max locals: "+maxLocals+"<"+locals.size());
		}
		for (int i=0;i<maxLocals-locals.size(); i++) {
			locals.add(VerificationType.TOP);
		}
		Stack<VerificationType> stack = new Stack<VerificationType>();
		return new Frame(locals, stack, maxStack);
	}
	
	public Frame applyStackmapSameLocalsOneStackItem(VerificationType stackItem) {
		Stack<VerificationType> stack = new Stack<VerificationType>();
		stack.push(stackItem);
		if (stackItem.getSize()>maxStackSize) {
			throw new StackmapSameLocalsStackOverflowException(-1, maxStackSize, stackItem.getSize());
		}
		return new Frame(locals, stack,maxStackSize);
	}
	
	public Frame applyStackmapChop(int chop) {
		if (chop>activeLocals) {
			throw new StackmapChopUnderflowException(-1,activeLocals,chop);
		}
		List<VerificationType> newLocals = new ArrayList<VerificationType>();
		for (int i=0;i<activeLocals-chop; i++) {
			newLocals.add(locals.get(i));
		}
		for (int i=0;i<(locals.size()-(activeLocals-chop)); i++) {
			newLocals.add(VerificationType.TOP);
		}
		Stack<VerificationType> stack = new Stack<VerificationType>();
		return new Frame(newLocals, stack,maxStackSize);
		
	}
	
	public Frame applyStackmapAppend(List<VerificationType> appendLocals) {
		int appendSize = 0;
		for (VerificationType t: appendLocals) {
			appendSize+=t.getSize();
		}
		if (activeLocals+appendSize>locals.size()) {
			throw new StackmapAppendOverflowException(-1,appendSize, activeLocals,locals.size());
		}
		List<VerificationType> newLocals = new ArrayList<VerificationType>();
		for (int i=0;i<activeLocals; i++) {
			newLocals.add(locals.get(i));
		}
		for (VerificationType t: appendLocals) {
			newLocals.add(t);
			if (t.getSize() == 2) {
				newLocals.add(VerificationType.TOP);
			}
		}
		Stack<VerificationType> stack = new Stack<VerificationType>();
		return new Frame(newLocals, stack,maxStackSize);
		
	}
	
	public Frame applyStackmapFull(List<VerificationType> fullLocals, List<VerificationType> fullStack) {
		int fullLocalsSize = 0;
		for (VerificationType t: fullLocals) {
			fullLocalsSize+=t.getSize();
		}
		
		int fullStackSize = 0;
		for (VerificationType t: fullStack) {
			fullStackSize+=t.getSize();
		}
		
		if (fullLocalsSize>locals.size()) {
			throw new StackmapFullLocalsOverflowException(-1, locals.size(), fullLocalsSize);
		}
		
		if (fullStackSize>maxStackSize) {
			throw new StackmapFullStackOverflowException(-1, maxStackSize, fullStackSize);
		}
		
		List<VerificationType> newLocals = new ArrayList<VerificationType>();
		for (VerificationType t: fullLocals) {
			newLocals.add(t);
			if (t.getSize() == 2) {
				newLocals.add(VerificationType.TOP);
			}
		}
		for (int i=0;i<locals.size()-newLocals.size(); i++) {
			newLocals.add(VerificationType.TOP);
		}
		Stack<VerificationType> stack = new Stack<VerificationType>();
		for (VerificationType t: fullStack) {
			stack.push(t);
		}
		return new Frame(newLocals, stack,maxStackSize);
		
	}
	
	private static VerificationType createTypeFromDescriptor(TypeDescriptor desc, IClassQuery query) {
		if (desc.isArray()) {
			return new ObjectValueType(desc, query);
		} else if (desc.isBoolean()) {
			return VerificationType.INT;
		} else if (desc.isByte()) {
			return VerificationType.INT;
		} else if (desc.isDouble()) {
			return VerificationType.DOUBLE;
		} else if (desc.isFloat()) {
			return VerificationType.FLOAT;
		} else if (desc.isInteger()) {
			return VerificationType.INT;
		} else if (desc.isLong()) {
			return VerificationType.LONG;
		} else if (desc.isObject()) {
			return new ObjectValueType(desc, query);
		} else if (desc.isShort()) {
			return VerificationType.INT;
		} else {
			throw new IllegalStateException("Unknwohn descriptor type: "+desc);
		}
	}
	
	/**
	 * Only for Tests
	 */
	public VerificationType getTypeOnStack(int index) {
		return stack.get(index);
	}
	
	public VerificationType getTypeInRegister(int index) {
		return locals.get(index);
	}
	
	public static Frame createFrame(List<VerificationType> locals, List<VerificationType> stackValues, int maxStackSize) {
		Stack<VerificationType> values = new Stack<VerificationType>();
		values.addAll(stackValues);
		return new Frame(locals, values,maxStackSize);
	}
	

}