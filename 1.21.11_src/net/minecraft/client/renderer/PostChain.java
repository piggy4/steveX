package net.minecraft.client.renderer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.shaders.UniformType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PostChain implements AutoCloseable {
	public static final Identifier MAIN_TARGET_ID = Identifier.withDefaultNamespace("main");
	private final List<PostPass> passes;
	private final Map<Identifier, PostChainConfig.InternalTarget> internalTargets;
	private final Set<Identifier> externalTargets;
	private final Map<Identifier, RenderTarget> persistentTargets = new HashMap<>();
	private final CachedOrthoProjectionMatrixBuffer projectionMatrixBuffer;

	private PostChain(
		List<PostPass> list,
		Map<Identifier, PostChainConfig.InternalTarget> map,
		Set<Identifier> set,
		CachedOrthoProjectionMatrixBuffer cachedOrthoProjectionMatrixBuffer
	) {
		this.passes = list;
		this.internalTargets = map;
		this.externalTargets = set;
		this.projectionMatrixBuffer = cachedOrthoProjectionMatrixBuffer;
	}

	public static PostChain load(
		PostChainConfig postChainConfig,
		TextureManager textureManager,
		Set<Identifier> set,
		Identifier identifier,
		CachedOrthoProjectionMatrixBuffer cachedOrthoProjectionMatrixBuffer
	) throws ShaderManager.CompilationException {
		Stream<Identifier> stream = postChainConfig.passes().stream().flatMap(PostChainConfig.Pass::referencedTargets);
		Set<Identifier> set2 = stream.filter(identifierx -> !postChainConfig.internalTargets().containsKey(identifierx)).collect(Collectors.toSet());
		Set<Identifier> set3 = Sets.difference(set2, set);
		if (!set3.isEmpty()) {
			throw new ShaderManager.CompilationException("Referenced external targets are not available in this context: " + set3);
		}

		Builder<PostPass> builder = ImmutableList.builder();

		for (int i = 0; i < postChainConfig.passes().size(); i++) {
			PostChainConfig.Pass pass = postChainConfig.passes().get(i);
			builder.add(createPass(textureManager, pass, identifier.withSuffix("/" + i)));
		}

		return new PostChain(builder.build(), postChainConfig.internalTargets(), set2, cachedOrthoProjectionMatrixBuffer);
	}

	private static PostPass createPass(TextureManager textureManager, PostChainConfig.Pass pass, Identifier identifier) throws ShaderManager.CompilationException {
		RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
			.withFragmentShader(pass.fragmentShaderId())
			.withVertexShader(pass.vertexShaderId())
			.withLocation(identifier);

		for (PostChainConfig.Input input : pass.inputs()) {
			builder.withSampler(input.samplerName() + "Sampler");
		}

		builder.withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER);

		for (String string : pass.uniforms().keySet()) {
			builder.withUniform(string, UniformType.UNIFORM_BUFFER);
		}

		RenderPipeline renderPipeline = builder.build();
		List<PostPass.Input> list = new ArrayList<>();

		for (PostChainConfig.Input input2 : pass.inputs()) {
			switch (input2) {
				case PostChainConfig.TextureInput(String string2, Identifier identifier2, int i, int j, boolean bl):
					AbstractTexture abstractTexture = textureManager.getTexture(identifier2.withPath(string -> "textures/effect/" + string + ".png"));
					list.add(new PostPass.TextureInput(string2, abstractTexture, i, j, bl));
					break;
				case PostChainConfig.TargetInput(String string3, Identifier identifier3, boolean bl2, boolean bl3):
					list.add(new PostPass.TargetInput(string3, identifier3, bl2, bl3));
					break;
				default:
					throw new MatchException(null, null);
			}
		}

		return new PostPass(renderPipeline, pass.outputTarget(), pass.uniforms(), list);
	}

	public void addToFrame(FrameGraphBuilder frameGraphBuilder, int i, int j, PostChain.TargetBundle targetBundle) {
		GpuBufferSlice gpuBufferSlice = this.projectionMatrixBuffer.getBuffer(i, j);
		Map<Identifier, ResourceHandle<RenderTarget>> map = new HashMap<>(this.internalTargets.size() + this.externalTargets.size());

		for (Identifier identifier : this.externalTargets) {
			map.put(identifier, targetBundle.getOrThrow(identifier));
		}

		for (Entry<Identifier, PostChainConfig.InternalTarget> entry : this.internalTargets.entrySet()) {
			Identifier identifier2 = entry.getKey();
			PostChainConfig.InternalTarget internalTarget = entry.getValue();
			RenderTargetDescriptor renderTargetDescriptor = new RenderTargetDescriptor(
				internalTarget.width().orElse(i), internalTarget.height().orElse(j), true, internalTarget.clearColor()
			);
			if (internalTarget.persistent()) {
				RenderTarget renderTarget = this.getOrCreatePersistentTarget(identifier2, renderTargetDescriptor);
				map.put(identifier2, frameGraphBuilder.importExternal(identifier2.toString(), renderTarget));
			} else {
				map.put(identifier2, frameGraphBuilder.createInternal(identifier2.toString(), renderTargetDescriptor));
			}
		}

		for (PostPass postPass : this.passes) {
			postPass.addToFrame(frameGraphBuilder, map, gpuBufferSlice);
		}

		for (Identifier identifier : this.externalTargets) {
			targetBundle.replace(identifier, map.get(identifier));
		}
	}

	@Deprecated
	public void process(RenderTarget renderTarget, GraphicsResourceAllocator graphicsResourceAllocator) {
		FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
		PostChain.TargetBundle targetBundle = PostChain.TargetBundle.of(MAIN_TARGET_ID, frameGraphBuilder.importExternal("main", renderTarget));
		this.addToFrame(frameGraphBuilder, renderTarget.width, renderTarget.height, targetBundle);
		frameGraphBuilder.execute(graphicsResourceAllocator);
	}

	private RenderTarget getOrCreatePersistentTarget(Identifier identifier, RenderTargetDescriptor renderTargetDescriptor) {
		RenderTarget renderTarget = this.persistentTargets.get(identifier);
		if (renderTarget == null || renderTarget.width != renderTargetDescriptor.width() || renderTarget.height != renderTargetDescriptor.height()) {
			if (renderTarget != null) {
				renderTarget.destroyBuffers();
			}

			renderTarget = renderTargetDescriptor.allocate();
			renderTargetDescriptor.prepare(renderTarget);
			this.persistentTargets.put(identifier, renderTarget);
		}

		return renderTarget;
	}

	@Override
	public void close() {
		this.persistentTargets.values().forEach(RenderTarget::destroyBuffers);
		this.persistentTargets.clear();

		for (PostPass postPass : this.passes) {
			postPass.close();
		}
	}

	@Environment(EnvType.CLIENT)
	public interface TargetBundle {
		static PostChain.TargetBundle of(Identifier identifier, ResourceHandle<RenderTarget> resourceHandle) {
			return new PostChain.TargetBundle() {
				private ResourceHandle<RenderTarget> handle = resourceHandle;

				@Override
				public void replace(Identifier identifier, ResourceHandle<RenderTarget> resourceHandle) {
					if (identifier.equals(identifier)) {
						this.handle = resourceHandle;
					} else {
						throw new IllegalArgumentException("No target with id " + identifier);
					}
				}

				@Override
				public @Nullable ResourceHandle<RenderTarget> get(Identifier identifier) {
					return identifier.equals(identifier) ? this.handle : null;
				}
			};
		}

		void replace(Identifier identifier, ResourceHandle<RenderTarget> resourceHandle);

		@Nullable ResourceHandle<RenderTarget> get(Identifier identifier);

		default ResourceHandle<RenderTarget> getOrThrow(Identifier identifier) {
			ResourceHandle<RenderTarget> resourceHandle = this.get(identifier);
			if (resourceHandle == null) {
				throw new IllegalArgumentException("Missing target with id " + identifier);
			} else {
				return resourceHandle;
			}
		}
	}
}
