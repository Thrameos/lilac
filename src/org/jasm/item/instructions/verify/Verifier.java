package org.jasm.item.instructions.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jasm.environment.Environment;
import org.jasm.item.IErrorEmitter;
import org.jasm.item.attribute.AbstractStackmapFrame;
import org.jasm.item.attribute.AbstractStackmapVariableinfo;
import org.jasm.item.attribute.AppendStackmapFrame;
import org.jasm.item.attribute.Attribute;
import org.jasm.item.attribute.ChopStackmapFrame;
import org.jasm.item.attribute.CodeAttributeContent;
import org.jasm.item.attribute.DoubleStackmapVariableinfo;
import org.jasm.item.attribute.ExceptionHandler;
import org.jasm.item.attribute.ExceptionHandlerTable;
import org.jasm.item.attribute.FloatStackmapVariableinfo;
import org.jasm.item.attribute.FullStackmapFrame;
import org.jasm.item.attribute.IntegerStackmapVariableinfo;
import org.jasm.item.attribute.LongStackmapVariableinfo;
import org.jasm.item.attribute.NullStackmapVariableinfo;
import org.jasm.item.attribute.ObjectStackmapVariableinfo;
import org.jasm.item.attribute.SameExtendedStackmapFrame;
import org.jasm.item.attribute.SameLocalsOneStackitemExtendedStackmapFrame;
import org.jasm.item.attribute.SameLocalsOneStackitemStackmapFrame;
import org.jasm.item.attribute.SameStackmapFrame;
import org.jasm.item.attribute.StackMapAttributeContent;
import org.jasm.item.attribute.TopStackmapVariableinfo;
import org.jasm.item.attribute.UninitializedStackmapVariableinfo;
import org.jasm.item.attribute.UninitializedThisStackmapVariableinfo;
import org.jasm.item.clazz.Clazz;
import org.jasm.item.clazz.IAttributesContainer;
import org.jasm.item.clazz.Method;
import org.jasm.item.constantpool.ClassInfo;
import org.jasm.item.instructions.AbstractInstruction;
import org.jasm.item.instructions.AbstractSwitchInstruction;
import org.jasm.item.instructions.ArgumentLessInstruction;
import org.jasm.item.instructions.BranchInstruction;
import org.jasm.item.instructions.ConstantPoolInstruction;
import org.jasm.item.instructions.Instructions;
import org.jasm.item.instructions.LocalVariableInstruction;
import org.jasm.item.instructions.OpCodes;
import org.jasm.item.instructions.verify.error.LinearMethodException;
import org.jasm.item.instructions.verify.error.MissingStackmapException;
import org.jasm.item.instructions.verify.error.MissingStackmapsDeclaration;
import org.jasm.item.instructions.verify.error.TypeCheckingException;
import org.jasm.item.instructions.verify.error.TypeInferencingException;
import org.jasm.item.instructions.verify.error.UnknownClassException;
import org.jasm.item.instructions.verify.types.DoubleType;
import org.jasm.item.instructions.verify.types.FloatType;
import org.jasm.item.instructions.verify.types.IClassQuery;
import org.jasm.item.instructions.verify.types.IntType;
import org.jasm.item.instructions.verify.types.LongType;
import org.jasm.item.instructions.verify.types.NullType;
import org.jasm.item.instructions.verify.types.ObjectValueType;
import org.jasm.item.instructions.verify.types.TopType;
import org.jasm.item.instructions.verify.types.UninitializedThisType;
import org.jasm.item.instructions.verify.types.UninitializedValueType;
import org.jasm.item.instructions.verify.types.VerificationType;
import org.jasm.parser.SourceLocation;
import org.jasm.parser.literals.AbstractLiteral;
import org.jasm.parser.literals.IntegerLiteral;
import org.jasm.resolver.ExternalClassInfo;
import org.jasm.type.descriptor.MethodDescriptor;
import org.jasm.type.descriptor.TypeDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Verifier implements IClassQuery {
	
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	private Instructions parent;
	
	private Clazz clazz;
	private Method method;
	private CodeAttributeContent code;
	private Interpreter interpeter;
	
	private int maxRecordedStackSize;
	
	
	private List<Set<Integer>> followers = new ArrayList<Set<Integer>>();
	private List<Set<ExceptionHandler>> exceptionHandlers = new ArrayList<Set<ExceptionHandler>>();
	private Set<Integer> branchTargets = new HashSet<Integer>();
	private Set<Integer> exceptionTargets = new HashSet<Integer>();
	private Map<Integer, Frame> stackMapFrames = new HashMap<Integer, Frame>();
	
	private Map<Integer, Frame> inferencedFrames = new HashMap<Integer, Frame>();
	
	private int currentInstructionIndex = -1;
	
	private boolean hasStackMapDeclaration;
	
	private Frame initialFrame = null;
	
	private boolean hasErrors;
	private boolean hasUnsupportedCode;
	
	public void setParent(Instructions parent) {
		this.parent = parent;
		this.interpeter = new Interpreter();
		this.interpeter.setParent(this);
	}
	
	private AbstractInstruction getInstructionAt(int index) {
		return parent.get(index);
	}
	
	private Set<Integer> getAllFollowersFor(int index) {
		Set<Integer> result = new HashSet<Integer>();
		result.addAll(followers.get(index));
		for (ExceptionHandler handler: exceptionHandlers.get(index)) {
			result.add(handler.getHandlerInstruction().getIndex());
		}
		
		return result;
	}
	
	private Set<Integer> getAllReachable(int index) {
		Set<Integer> result = new HashSet<Integer>();
		getAllReachable(index, result);
		return result;
	}

	
	private void getAllReachable(int index, Set<Integer> result) {
		List<Integer> unvisited = new ArrayList<Integer>();
		unvisited.add(index);
		while (unvisited.size() > 0) {
			Integer i = unvisited.remove((int)0);
			result.add(i);
			for (Integer f: getAllFollowersFor(i)) {
				if (!unvisited.contains(f) && !result.contains(f)) {
					unvisited.add(f);
				}
			}
		}
		
	}
	
	public void verifyStage1() {
		
		this.clazz = parent.getAncestor(Clazz.class);
		this.method = parent.getAncestor(Method.class);
		this.code = parent.getAncestor(CodeAttributeContent.class);
		
		
		double version = clazz.getDecimalVersion().doubleValue();
		checkForBadCode(version);
		if (!hasUnsupportedCode) {
			calculateFollowers(); 
			if (!hasErrors) {
				checkAllReachable();
			}
			
		}
	}
	
	public void verify() {
		try {
			double version = clazz.getDecimalVersion().doubleValue();
			maxRecordedStackSize = -1;
			createInitialFrame();
			if (version>=50 && !generateStackmap()) {
				try {
					doTypeChecking();
				} catch (VerifyException e) {
					if (version>50) {
						throw e;
					} else {
						try {
							doTypeInferencing();
						} catch (TypeInferencingException e1) {
							throw e1.getCause();
						} catch (LinearMethodException e1) {
							throw e1.getCause();
						}
					}
				} catch (LinearMethodException e) {
					throw e.getCause();
				} catch (TypeCheckingException e) {
					throw e.getCause();
				}
			} else {
				try {
					doTypeInferencing();
				} catch (TypeInferencingException e) {
					throw e.getCause();
				} catch (LinearMethodException e) {
					throw e.getCause();
				}
				
			}
		} catch (VerifyException e) {
			emitCodeVerifyError(e);
		} catch (RuntimeException e) {
			emitRuntimeError(e);
		}
		
		
	}
	
	private void createInitialFrame() {
		String className = clazz.getThisClass().getClassNameReference().getValue();
		boolean isConstructor = method.getName().getValue().equals("<init>");
		boolean isStatic = method.getModifier().isStatic();
		MethodDescriptor desc = method.getMethodDescriptor();
		int maxLocals = code.getMaxLocals();
		int maxStack = code.getMaxStack();
		if (maxStack < 0) {
			maxStack = Integer.MAX_VALUE;
		}
		
		initialFrame = Frame.createInitialFrame(className, isConstructor, isStatic, maxLocals, maxStack, desc, this);
	}
	
	private boolean hasBranches() {
		return !(branchTargets.isEmpty() && exceptionTargets.isEmpty());
	}
	
	private void doTypeChecking() {
		List<Attribute> attrs = code.getAttributes().getAttributesByContentType(StackMapAttributeContent.class);
		if (attrs.size() == 0 && hasBranches()) {
			throw new MissingStackmapsDeclaration();
		}
		if (attrs.size() == 2) {
			throw new IllegalStateException("multiple stackmaps declared!");
		}
		hasStackMapDeclaration = (attrs.size() == 1);
		if (hasStackMapDeclaration) {
			createStackmapFrames((StackMapAttributeContent)attrs.get(0).getContent());
		}
		for (Integer b: branchTargets) {
			if (!stackMapFrames.containsKey(b)) {
				throw new MissingStackmapException(b);
			}
		}
		for (Integer b: exceptionTargets) {
			if (!stackMapFrames.containsKey(b)) {
				throw new MissingStackmapException(b);
			}
		}
		
		if (branchTargets.size() == 0 && 
				exceptionTargets.size() == 0 &&
				!hasStackMapDeclaration) {
			checkLinearMethod();
		} else {
			doRealTypeChecking();
		}
	}
	
	public void doTypeInferencing() {
		if (branchTargets.size() == 0 && 
				exceptionTargets.size() == 0) {
			checkLinearMethod();
		} else {
			doRealTypeInferencing();
		}
	}
	
	private void checkLinearMethod() {
		
		try {
			Frame currentFrame = initialFrame.copy();
			for (int i=0;i<parent.getSize(); i++) {
				currentInstructionIndex = i;
				AbstractInstruction instr = parent.get(currentInstructionIndex);
				if (instr instanceof BranchInstruction) {
					throw new IllegalStateException("Branch in a linear method");
				}
				if (instr instanceof AbstractSwitchInstruction)  {
					throw new IllegalStateException("Switch in a linear method");
				}
				if (instr instanceof ArgumentLessInstruction) {
					ArgumentLessInstruction ai = (ArgumentLessInstruction)instr;
					if (ai.isReturn() && currentInstructionIndex <(parent.getSize()-1)) {
						throw new IllegalStateException("return in the middle of a linear method");
					}
				}
				interpeter.execute(instr, currentFrame);
				
			}
		} catch (VerifyException e) {
			throw new LinearMethodException(e);
		}
	}
	
	private void doRealTypeChecking() {
		try {
			Frame nextFrame = initialFrame.copy();
			for (int i=0;i<parent.getSize(); i++) {
				Frame currentFrame = nextFrame;
				if (stackMapFrames.containsKey(i)) {
					currentFrame = stackMapFrames.get(i).copy();
				}
				currentInstructionIndex = i;
				AbstractInstruction instr = parent.get(currentInstructionIndex);
				nextFrame = interpeter.execute(instr, currentFrame).copy();
				Set<Integer> myFollowers = followers.get(i);
				for (Integer f: myFollowers) {
					if (stackMapFrames.containsKey(f)) {
						Frame stackmapFrame = stackMapFrames.get(f);
						if (!stackmapFrame.isAssignableFrom(nextFrame)) {
							throw new VerifyException(currentInstructionIndex, "current stackframe isn't assignable to the stack frame at "+f);
						}
					}
				}
				
				for (ExceptionHandler handler: exceptionHandlers.get(i)) {
					ClassInfo exception = handler.getCatchType();
					ObjectValueType exceptionType;
					if (exception != null) {
						exceptionType = new ObjectValueType("L"+exception.getClassName()+";", this);
					} else {
						exceptionType = VerificationType.THROWABLE.create(this);
					}
					Frame stackmapFrame = stackMapFrames.get(handler.getHandlerInstruction().getIndex());
					if (!stackmapFrame.isAssignableFrom(nextFrame.copy().throwException(exceptionType))) {
						throw new VerifyException(currentInstructionIndex, "current stackframe isn't assignable to the stack frame at "+handler.getHandlerInstruction().getIndex());
					}
					
				}
				
				
			}
		} catch (VerifyException e) {
			throw new TypeCheckingException(e);
		}
	}
	
	private void doRealTypeInferencing() {
		if (log.isDebugEnabled()) {
			log.debug("type inferencing for: "+method.getName());
		}
		try {
			Frame firstFrame = initialFrame.copy();
			inferencedFrames.put(0, firstFrame);
			Set<Integer> changed = new HashSet<Integer>();
			changed.add(0);
			while (!changed.isEmpty()) {
				if (log.isDebugEnabled()) {
					log.debug("changed = "+changed);
				}
				currentInstructionIndex = changed.iterator().next();
				
				AbstractInstruction instr = parent.get(currentInstructionIndex);
				if (log.isDebugEnabled()) {
					log.debug("Handling instruction at "+instr.getIndex()+":"+instr.getOffsetInCode());
				}
				changed.remove(currentInstructionIndex);
				Frame currentFrame = inferencedFrames.get(currentInstructionIndex);
				if (log.isDebugEnabled()) {
					log.debug("current frame: "+currentFrame);
				}
				Set<Integer> myFollowers = followers.get(currentInstructionIndex);
				Set<ExceptionHandler> myExceptionHandlers = exceptionHandlers.get(currentInstructionIndex);
				Frame nextFrame = interpeter.execute(instr, currentFrame.copy());
				for (int f: myFollowers) {
					if (log.isDebugEnabled()) {
						AbstractInstruction fInstr = parent.get(f);
						log.debug("Handling follower at instruction at "+fInstr.getIndex()+":"+fInstr.getOffsetInCode());
					}
					Frame followerFrame = inferencedFrames.get(f);
					if (followerFrame == null) {
						changed.add(f);
						Frame fr = nextFrame.copy();
						inferencedFrames.put(f, fr);
						if (log.isDebugEnabled()) {
							log.debug("Set frame "+fr);
						}
					} else if (followerFrame.equals(nextFrame)) {
						if (log.isDebugEnabled()) {
							log.debug(followerFrame+" EQUALS "+nextFrame);
						}
					} else {
						Frame fr = nextFrame.merge(followerFrame);
						if (log.isDebugEnabled()) {
							log.debug("Merge "+followerFrame+" with "+nextFrame+"-->"+fr);
						}
						if (!fr.equals(followerFrame)) {
							changed.add(f);
							inferencedFrames.put(f, fr);
						} else {
							if (log.isDebugEnabled()) {
								log.debug(followerFrame+" EQUALS "+fr);
							}
						}
					}
				}
				
				for (ExceptionHandler h: myExceptionHandlers) {
					ClassInfo exception = h.getCatchType();
					ObjectValueType exceptionType;
					if (exception != null) {
						exceptionType = new ObjectValueType("L"+exception.getClassName()+";", this);
					} else {
						exceptionType = VerificationType.THROWABLE.create(this);
					}
					if (log.isDebugEnabled()) {
						log.debug("Handling exception handler at "+h.getHandlerInstruction().getIndex()+":"+h.getHandlerInstruction().getOffsetInCode());
					}
					int index = h.getHandlerInstruction().getIndex();
					Frame followerFrame = inferencedFrames.get(index);
					Frame exceptionFrame = nextFrame.copy().throwException(exceptionType);
					if (log.isDebugEnabled()) {
						log.debug("Exception frame:  "+exceptionFrame);
					}
					if (followerFrame == null) {
						changed.add(index);
						inferencedFrames.put(index, exceptionFrame);
						if (log.isDebugEnabled()) {
							log.debug("Set Frame to:  "+exceptionFrame);
						}
					} else if (followerFrame.equals(exceptionFrame)) {
						if (log.isDebugEnabled()) {
							log.debug(followerFrame+" EQUALS "+exceptionFrame);
						}
					} else {
						Frame fr = exceptionFrame.merge(followerFrame);
						if (log.isDebugEnabled()) {
							log.debug("Merge "+exceptionFrame+" with "+followerFrame+"-->"+fr);
						}
						if (!fr.equals(followerFrame)) {
							changed.add(index);
							inferencedFrames.put(index, fr);
						} else {
							if (log.isDebugEnabled()) {
								log.debug(followerFrame+" EQUALS "+fr);
							}
						}
					}
					if (changed.contains(currentInstructionIndex)) {
						if (log.isDebugEnabled()) {
							log.debug("Loop at "+instr.getIndex()+":"+instr.getOffsetInCode());
						}
					}
				}
			}
			
			if ((inferencedFrames.size() != parent.getSize())) {
				throw new IllegalStateException(inferencedFrames.size()+"!="+parent.getSize());
			}
			
			double version = clazz.getDecimalVersion().doubleValue();
			if (generateStackmap() && version>=50) {
				generateStackMap();
			}
			
		} catch (VerifyException e) {
			throw new TypeInferencingException(e);
		}
	}
	
	private void generateStackMap() {
		StackMapAttributeContent content = getStackmapCreatingIfNecessary();
		content.setSourceLocation(null);
		Attribute parent = (Attribute)content.getParent();
		parent.setResolved(false);
		content.setResolved(false);
		content.clear();
		
		//Adding frames
		Set<Integer> indexes = new HashSet<Integer>();
		indexes.addAll(branchTargets);
		indexes.addAll(exceptionTargets);
		List<Integer> indexesList = new ArrayList<Integer>();
		indexesList.addAll(indexes);
		Collections.sort(indexesList);
		Frame previousFrame = initialFrame;
		for (int i=0;i<indexesList.size(); i++) {
			Frame frame = inferencedFrames.get(indexesList.get(i));
			AbstractInstruction instr = getInstructionAt(indexesList.get(i));
			AbstractInstruction previousInstruction = null;
			if (i>0) {
				previousInstruction = getInstructionAt(indexesList.get(i-1));
			}
			int offsetDelta = -1;
			if (previousInstruction == null) {
				offsetDelta = instr.getOffsetInCode();
			} else {
				offsetDelta = instr.getOffsetInCode()-previousInstruction.getOffsetInCode()-1;
			}
			content.add(createStackMapFrame(indexesList.get(i), frame, previousFrame, offsetDelta));
			previousFrame = frame;
		}
		
		
		parent.resolve();
	}
	
	private void createStackmapFrames(StackMapAttributeContent stackmapContent) {
		Frame previousFrame = initialFrame;
		for (int i=0;i<stackmapContent.getSize(); i++) {
			AbstractStackmapFrame stackMapFrame = stackmapContent.get(i);
			int index = stackMapFrame.getInstruction().getIndex();
			currentInstructionIndex = index;
			if (stackMapFrames.containsKey(index)) {
				throw new IllegalStateException("index "+index+" already has a stack frame!");
			}
			Frame fr = createFrameFromStackFrame(previousFrame, stackMapFrame);
			stackMapFrames.put(index, fr);
			previousFrame = fr;
			currentInstructionIndex = -1;
		}
		
	}
	
	private Frame createFrameFromStackFrame(Frame previousFrame, AbstractStackmapFrame stackMapFrame) {
		if (stackMapFrame instanceof SameStackmapFrame) {
			return previousFrame.applyStackmapSame();
		} else if (stackMapFrame instanceof SameExtendedStackmapFrame) {
			return previousFrame.applyStackmapSame();
		} else if (stackMapFrame instanceof SameLocalsOneStackitemStackmapFrame) {
			SameLocalsOneStackitemStackmapFrame slo = (SameLocalsOneStackitemStackmapFrame)stackMapFrame;
			return previousFrame.applyStackmapSameLocalsOneStackItem(createVerificationTypeFromStackMap(slo.getStackitemInfo()));
		} else if (stackMapFrame instanceof SameLocalsOneStackitemExtendedStackmapFrame) {
			SameLocalsOneStackitemExtendedStackmapFrame slo = (SameLocalsOneStackitemExtendedStackmapFrame)stackMapFrame;
			return previousFrame.applyStackmapSameLocalsOneStackItem(createVerificationTypeFromStackMap(slo.getStackitemInfo()));
		} else if (stackMapFrame instanceof ChopStackmapFrame) {
			ChopStackmapFrame slo = (ChopStackmapFrame)stackMapFrame;
			return previousFrame.applyStackmapChop(slo.getK());
		} else if (stackMapFrame instanceof AppendStackmapFrame) {
			AppendStackmapFrame slo = (AppendStackmapFrame)stackMapFrame;
			List<VerificationType> locals = new ArrayList<VerificationType>();
			for (AbstractStackmapVariableinfo info: slo.getLocals()) {
				locals.add(createVerificationTypeFromStackMap(info));
			}
			return previousFrame.applyStackmapAppend(locals);
		} else if (stackMapFrame instanceof FullStackmapFrame) {
			FullStackmapFrame slo = (FullStackmapFrame)stackMapFrame;
			List<VerificationType> locals = new ArrayList<VerificationType>();
			for (AbstractStackmapVariableinfo info: slo.getLocals()) {
				locals.add(createVerificationTypeFromStackMap(info));
			}
			List<VerificationType> stack = new ArrayList<VerificationType>();
			for (AbstractStackmapVariableinfo info: slo.getStackItems()) {
				stack.add(createVerificationTypeFromStackMap(info));
			}
			return previousFrame.applyStackmapFull(locals, stack);
		} else {
			throw new IllegalArgumentException("unknown stackmap frame type: "+stackMapFrame);
		}
	}
	
	private AbstractStackmapFrame createStackMapFrame(int index, Frame frame, Frame prevousFrame, int offsetDelta) {
		AbstractFrameDifference diff = prevousFrame.calculateFrameDifference(frame);
		//diff = frame.createFullFrame();
		AbstractStackmapFrame result = null;
		if (offsetDelta<0) {
			throw new IllegalArgumentException(""+offsetDelta);
		}
		AbstractInstruction instr = getInstructionAt(index);
		if (diff instanceof SameFrame && offsetDelta<=63) {
			result = new SameStackmapFrame();
		} else if (diff instanceof SameFrame && offsetDelta>63) {
			result =  new SameExtendedStackmapFrame();
		} else if (diff instanceof SameLocalsOneStackItemFrame && offsetDelta<=63) {
			SameLocalsOneStackItemFrame value = (SameLocalsOneStackItemFrame)diff;
			SameLocalsOneStackitemStackmapFrame result2 = new SameLocalsOneStackitemStackmapFrame();
			AbstractStackmapVariableinfo vinfo = createStackmapVariableInfoFromVerificationType(value.getStackItem(), index);
			result2.addVariableInfo(vinfo);
			result = result2;
		} else if (diff instanceof SameLocalsOneStackItemFrame && offsetDelta>63) {
			SameLocalsOneStackItemFrame value = (SameLocalsOneStackItemFrame)diff;
			SameLocalsOneStackitemExtendedStackmapFrame result2 = new SameLocalsOneStackitemExtendedStackmapFrame();
			AbstractStackmapVariableinfo vinfo = createStackmapVariableInfoFromVerificationType(value.getStackItem(), index);
			result2.addVariableInfo(vinfo);
			result = result2;
		} else if (diff instanceof ChopFrame) {
			ChopFrame value = (ChopFrame)diff;
			ChopStackmapFrame result2 = new ChopStackmapFrame();
			result2.setkLiteral(new IntegerLiteral(instr.getSourceLocation().getLine(),instr.getSourceLocation().getCharPosition(),value.getCount()+""));
			result = result2;
		} else if (diff instanceof AppendFrame) {
			AppendFrame value = (AppendFrame)diff;
			AppendStackmapFrame result2 = new AppendStackmapFrame();
			for (VerificationType t: value.getLocals()) {
				result2.addVariableInfo(createStackmapVariableInfoFromVerificationType(t, index));
			}
			result = result2;
		} else if (diff instanceof FullFrame) {
			FullFrame value = (FullFrame)diff;
			FullStackmapFrame result2 = new FullStackmapFrame();
			for (VerificationType t: value.getLocals()) {
				result2.addVariableInfo(createStackmapVariableInfoFromVerificationType(t, index));
			}
			result2.switchAddingToStackItems();
			for (VerificationType t: value.getStack()) {
				result2.addVariableInfo(createStackmapVariableInfoFromVerificationType(t, index));
			}
			result = result2;
		} else {
			throw new IllegalArgumentException("Unknown type: "+diff.getClass().getName());
		}
		
		result.setInstruction(getInstructionAt(index));
		return result;
		
	}
	
	private VerificationType createVerificationTypeFromStackMap(AbstractStackmapVariableinfo info) {
		if (info instanceof DoubleStackmapVariableinfo) {
			return VerificationType.DOUBLE;
		} else if (info instanceof FloatStackmapVariableinfo) {
			return VerificationType.FLOAT;
		} else if (info instanceof IntegerStackmapVariableinfo) {
			return VerificationType.INT;
		} else if (info instanceof LongStackmapVariableinfo) {
			return VerificationType.LONG;
		} else if (info instanceof NullStackmapVariableinfo) {
			return VerificationType.NULL;
		} else if (info instanceof ObjectStackmapVariableinfo) {
			ObjectStackmapVariableinfo osvi = (ObjectStackmapVariableinfo)info;
			ClassInfo cli = osvi.getClassInfo();
			if (cli.isArray()) {
				return new ObjectValueType(new TypeDescriptor(cli.getClassName()), this);
			} else {
				return new ObjectValueType(new TypeDescriptor("L"+cli.getClassName()+";"), this);
			}
		} else if (info instanceof TopStackmapVariableinfo) {
			return VerificationType.TOP;
		} else if (info instanceof UninitializedStackmapVariableinfo) {
			UninitializedStackmapVariableinfo usv = (UninitializedStackmapVariableinfo)info;
			ConstantPoolInstruction instr = usv.getInstruction(); 
			ClassInfo cli = (ClassInfo)instr.getCpEntry();
			if (cli.isArray()) {
				throw new IllegalStateException("new isn't allowed for class types");
			}
			return new UninitializedValueType(new TypeDescriptor("L"+cli.getClassName()+";"), instr.getIndex());
		} else if (info instanceof UninitializedThisStackmapVariableinfo) {
			return VerificationType.UNINITIALIZED_THIS;
		} else {
			throw new IllegalArgumentException("Unknown stackmap variable info: "+info);
		}
	}
	
	private AbstractStackmapVariableinfo createStackmapVariableInfoFromVerificationType(VerificationType type, int index) {
		if (type instanceof DoubleType) {
			return new DoubleStackmapVariableinfo();
		} else if (type instanceof FloatType) {
			return new FloatStackmapVariableinfo();
		} else if (type instanceof IntType) {
			return new IntegerStackmapVariableinfo();
		} else if (type instanceof LongType) {
			return new LongStackmapVariableinfo();
		} else if (type instanceof NullType) {
			return new NullStackmapVariableinfo();
		} else if (type instanceof ObjectValueType) {
			
			ObjectValueType value = (ObjectValueType)type;
			String className = null;
			if (value.getDesc().isArray()) {
				className = value.getDesc().getValue();
			} else {
				className = value.getDesc().getClassName();
			}
			ObjectStackmapVariableinfo osvi = new ObjectStackmapVariableinfo();
			ClassInfo cli = clazz.getConstantPool().getOrAddClassInfo(className);
			if (!cli.isVerified()) {
				cli.verify();
			}
			if (cli.hasErrors()) {
				throw new VerifyException(index, "couldn't resolve class "+className);
			}
			osvi.setClassInfo(cli);
			return osvi;
		} else if (type instanceof TopType) {
			return new TopStackmapVariableinfo();
		} else if (type instanceof UninitializedValueType) {
			UninitializedValueType value = (UninitializedValueType)type;
			if (value.getDesc().isArray()) {
				throw new IllegalStateException("new isn't allowed for class types");
			}
			UninitializedStackmapVariableinfo usv = new UninitializedStackmapVariableinfo();
			usv.setInstruction((ConstantPoolInstruction)getInstructionAt(value.getInstructionIndex()));
			return usv;
		} else if (type instanceof UninitializedThisType) {
			return new UninitializedThisStackmapVariableinfo();
		} else {
			throw new IllegalArgumentException("Unknown verification type");
		}
	}
	
	private void checkForBadCode(double version) {
		for (int i=0;i<parent.getSize(); i++) {
			AbstractInstruction instr = parent.get(i);
			checkForBadCode(instr, version);
		}
	}
	
	
	
	private void calculateFollowers() {
		followers = new ArrayList<Set<Integer>>();
		exceptionHandlers = new ArrayList<Set<ExceptionHandler>>();
		
		//Normal followers
		for (int i=0;i<parent.getSize(); i++) {
			AbstractInstruction instr = parent.get(i);
			Set<Integer> instrFollowers = new HashSet<Integer>();
			followers.add(instrFollowers);
			Set<ExceptionHandler> instrHandlers = new HashSet<ExceptionHandler>();
			exceptionHandlers.add(instrHandlers);
			if (instr instanceof BranchInstruction) {
				BranchInstruction bi = (BranchInstruction)instr;
				if (instr.getOpCode() == OpCodes.jsr || instr.getOpCode() == OpCodes.jsr_w) {
					throw new IllegalStateException("Bad or unsupported code");
				} else {
					instrFollowers.add(bi.getTargetInst().getIndex());
					branchTargets.add(bi.getTargetInst().getIndex());
				}
				if (instr.getOpCode() == OpCodes.goto_ || instr.getOpCode() == OpCodes.goto_w) {
					
				} else {
					int nextIndex = instr.getIndex()+1;
					if (nextIndex>=parent.getSize()) {
						emitStage1Error(instr, "inexpected code end");
					} else {
						instrFollowers.add(nextIndex);
					}
				}
			} else if (instr instanceof AbstractSwitchInstruction) {
				AbstractSwitchInstruction ai = (AbstractSwitchInstruction)instr;
				instrFollowers.add(ai.getDefaultTarget().getIndex());
				branchTargets.add(ai.getDefaultTarget().getIndex());
				for (AbstractInstruction instr1: ai.getTargets()) {
					instrFollowers.add(instr1.getIndex());
					branchTargets.add(instr1.getIndex());
				}
			} else if (instr instanceof ArgumentLessInstruction) {
				ArgumentLessInstruction ai = (ArgumentLessInstruction)instr;
				if (ai.isReturn() || ai.getOpCode() == OpCodes.athrow) {
					//Returns and throws don't have followers
				} else {
					int nextIndex = instr.getIndex()+1;
					if (nextIndex>=parent.getSize()) {
						emitStage1Error(instr, "inexpected code end");
					} else {
						instrFollowers.add(nextIndex);
					}
					
				}
			} else if (instr instanceof LocalVariableInstruction ) {
				LocalVariableInstruction li = (LocalVariableInstruction)instr;
				if (li.getOpCode() == OpCodes.ret) {
					throw new IllegalStateException("Bad or unsupported code");
				} else {
					int nextIndex = instr.getIndex()+1;
					if (nextIndex>=parent.getSize()) {
						emitStage1Error(instr, "inexpected code end");
					} else {
						instrFollowers.add(nextIndex);
					}
				}
			} else {
				int nextIndex = instr.getIndex()+1;
				if (nextIndex>=parent.getSize()) {
					emitStage1Error(instr, "inexpected code end");
				} else {
					instrFollowers.add(nextIndex);
				}
			}
		}
		
		//Exception handlers
		ExceptionHandlerTable table = code.getExceptionTable();
		for (int i=0;i<table.getSize(); i++) {
			ExceptionHandler handler = table.get(i);
			exceptionTargets.add(handler.getHandlerInstruction().getIndex());
			for (int j=handler.getStartInstruction().getIndex(); j<=handler.getEndInstruction().getIndex();  j++) {
				exceptionHandlers.get(j).add(handler);
			}
		}
	}
	
	private void checkAllReachable() {
		Set<Integer> reachables = getAllReachable(0);
		List<Integer> unreachables = new ArrayList<Integer>();
		for (int i=0;i<parent.getSize(); i++) {
			if (!reachables.contains(i)) {
				unreachables.add(i);
			}
		}
		
		while (unreachables.size()>0) {
			emitStage1Error(getInstructionAt(unreachables.get(0)), "dead code");
			reachables = getAllReachable(unreachables.get(0));
			unreachables.removeAll(reachables);
		}
	}
	
	
	
	private void checkForBadCode(AbstractInstruction instr, double version) {
		if (instr.getOpCode() == OpCodes.jsr || 
			instr.getOpCode() == OpCodes.jsr_w||
			instr.getOpCode() == OpCodes.ret) {
			hasUnsupportedCode = true;
			if (version>50.0) {
				emitStage1Error(instr, "bad instruction");
			}
		}
		if (instr instanceof ArgumentLessInstruction && ((ArgumentLessInstruction)instr).isReturn()) {
			MethodDescriptor desc = method.getMethodDescriptor();
			if (
					(
					(instr.getOpCode() == OpCodes.areturn) 
					&& 
					!desc.isVoid() && (desc.getReturnType().isArray() 
					|| desc.getReturnType().isObject())
					) ||
					
					(
					(instr.getOpCode() == OpCodes.dreturn) 
					&& 
					!desc.isVoid() && (desc.getReturnType().isDouble())
					) ||
					
					(
					(instr.getOpCode() == OpCodes.freturn) 
					&& 
					!desc.isVoid() && (desc.getReturnType().isFloat())
					) ||
					
					(
					(instr.getOpCode() == OpCodes.ireturn) 
					&& 
					!desc.isVoid() && (desc.getReturnType().isByte() 
							|| desc.getReturnType().isBoolean()
							|| desc.getReturnType().isCharacter()
							|| desc.getReturnType().isShort()
							|| desc.getReturnType().isInteger())
					) ||
					(
					(instr.getOpCode() == OpCodes.lreturn) 
					&& 
					!desc.isVoid() && (desc.getReturnType().isLong())
					)
					 ||
					((instr.getOpCode() == OpCodes.return_) 
					&& 
					desc.isVoid()
					)
			) {
			  //correct
			
			} else {
				hasUnsupportedCode = true;
				emitStage1Error(instr, "return instruction doesn't match the return type "+desc.getValue());	
			}
				
		}
	}

	@Override
	public boolean isInterface(String className) {
		ExternalClassInfo classInfo = getClass(className);
		return classInfo.getModifier().isInterface();
	}
	
	//Class Query

	@Override
	public boolean isAssignable(String classTo, String classFrom) {
		
		ExternalClassInfo classToInfo = getClass(classTo);
		ExternalClassInfo classFromInfo = getClass(classFrom);

		return classFromInfo.isAssignableTo(classToInfo);
	}

	@Override
	public String merge(String classTo, String classFrom) {
		if (classTo.equals(classFrom)) {
			return classTo;
		} else if (classTo.equals("java/lang/Object") || classFrom.equals("java/lang/Object")) {
			return "java/lang/Object";
		} else {
			
			while (!isAssignable(classTo, classFrom)) {
				ExternalClassInfo cl = getClass(classTo);
				classTo = cl.getSuperName();
			}
			return classTo;
		}
	}
	
	private ExternalClassInfo getClass(String name) {
		IErrorEmitter dummyEmitter = new IErrorEmitter() {
			
			@Override
			public void emitErrorOnLocation(SourceLocation sl, String message) {
				
				
			}
			
			@Override
			public void emitError(AbstractLiteral literal, String message) {
				
				
			}

			@Override
			public void emitInternalError(Throwable e) {
				
				
			}
		};
		ExternalClassInfo result = clazz.checkAndLoadClassInfo(dummyEmitter, null, name, false);
		if (result == null) {
			throw new UnknownClassException(-1, name);
		}
		return result;
	}
	
	private void emitStage1Error(AbstractInstruction instr, String message) {
		hasErrors = true;
		instr.emitError(null, message);
	}
	
	private void emitCodeVerifyError(VerifyException e) {
		int index = 0;
		if (e.getInstructionIndex()>=0) {
			index = e.getInstructionIndex();
		} else if (currentInstructionIndex>=0) {
			index = currentInstructionIndex;
		} 
		AbstractInstruction instr = getInstructionAt(index);
		instr.emitError(null, "code verification error - "+e.getMessage());
		
	}
	
	private void emitRuntimeError(RuntimeException e) {
		log.error("code verification runtime error",e);
		int index = 0;
		if (currentInstructionIndex>=0) {
			index = currentInstructionIndex;
		} 
		AbstractInstruction instr = getInstructionAt(index);
		instr.emitInternalError(e);
		
	}

	public Method getMethod() {
		return method;
	}

	public Clazz getClazz() {
		return clazz;
	}

	public boolean isHasErrors() {
		return hasErrors;
	}

	public boolean isHasUnsupportedCode() {
		return hasUnsupportedCode;
	}
	
	private StackMapAttributeContent getStackmapCreatingIfNecessary() {
		StackMapAttributeContent content =  IAttributesContainer.getUniqueAttributeContentCreatingIfNecessary(StackMapAttributeContent.class, code);
		return content;
	}
	
	public void updateMaxRecordedStackSize(int newSize) {
		maxRecordedStackSize = Math.max(newSize, maxRecordedStackSize);
	}
	
	public int getMaxRecordedStackSize() {
		return maxRecordedStackSize;
	}

	private boolean generateStackmap() {
		return method.isGenerateStackMap() || Environment.getBooleanValue("jasm.forcestackmaps");
	}
	
}
