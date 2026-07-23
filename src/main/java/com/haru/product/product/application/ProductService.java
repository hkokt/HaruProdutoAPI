package com.haru.product.product.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.haru.product.product.application.dto.AddProductComponentRequest;
import com.haru.product.product.application.dto.CreateProductRequest;
import com.haru.product.product.application.dto.ProductCompositionResponse;
import com.haru.product.product.application.dto.ProductCompositionTreeResponse;
import com.haru.product.product.application.dto.ProductDeletionResult;
import com.haru.product.product.application.dto.ProductResponse;
import com.haru.product.product.application.dto.UpdateProductComponentRequest;
import com.haru.product.product.application.dto.UpdateProductRequest;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductComposition;
import com.haru.product.product.domain.ProductCompositionCycleValidator;
import com.haru.product.product.domain.exception.DuplicateProductComponentException;
import com.haru.product.product.domain.exception.InvalidProductCompositionException;
import com.haru.product.product.domain.exception.ProductCompositionCycleException;
import com.haru.product.product.domain.exception.ProductNotFoundException;
import com.haru.product.product.infrastructure.persistence.ProductCompositionRepository;
import com.haru.product.product.infrastructure.persistence.ProductCompositionTopologyLock;
import com.haru.product.product.infrastructure.persistence.PostgreSqlProductSkuGenerator;
import com.haru.product.product.infrastructure.persistence.ProductRepository;

@Service
@Transactional(readOnly = true)
public class ProductService {

	/** Maximum number of component levels returned by the composition-tree endpoint. */
	private static final int MAX_COMPOSITION_TREE_DEPTH = 32;
	/** Maximum number of component occurrences loaded or rendered in one tree response. */
	private static final int MAX_COMPOSITION_TREE_NODES = 5_000;
	/** Maximum number of parent product IDs sent in one composition query. */
	private static final int COMPOSITION_FETCH_BATCH_SIZE = 200;
	/** Maximum number of composition rows materialized by one tree query. */
	private static final int COMPOSITION_RESULT_PAGE_SIZE = 200;

	private final ProductRepository productRepository;
	private final ProductCompositionRepository productCompositionRepository;
	private final ProductCompositionTopologyLock topologyLock;
	private final ProductCompositionCycleValidator cycleValidator;
	private final PostgreSqlProductSkuGenerator skuGenerator;

	public ProductService(
			ProductRepository productRepository,
			ProductCompositionRepository productCompositionRepository,
			ProductCompositionTopologyLock topologyLock,
			ProductCompositionCycleValidator cycleValidator,
			PostgreSqlProductSkuGenerator skuGenerator) {
		this.productRepository = productRepository;
		this.productCompositionRepository = productCompositionRepository;
		this.topologyLock = topologyLock;
		this.cycleValidator = cycleValidator;
		this.skuGenerator = skuGenerator;
	}

	@Transactional
	public ProductResponse create(CreateProductRequest request) {
		Product product = Product.create(
				request.name(),
				request.description(),
				skuGenerator.nextSku(),
				request.type(),
				request.defaultMeasurementUnit());
		if (Boolean.FALSE.equals(request.active())) {
			product.deactivate();
		}

		return toResponse(productRepository.saveAndFlush(product), List.of());
	}

	public ProductResponse getById(Long id) {
		Product product = requireProduct(id);
		return toResponse(product, findDirectCompositions(product.getId()));
	}

	@Transactional
	public ProductResponse update(Long id, UpdateProductRequest request) {
		Product product = requireProduct(id);
		product.update(
				request.name(),
				request.description(),
				request.type(),
				request.defaultMeasurementUnit(),
				Boolean.TRUE.equals(request.active()));
		Product savedProduct = productRepository.saveAndFlush(product);
		return toResponse(savedProduct, findDirectCompositions(savedProduct.getId()));
	}

	@Transactional
	public ProductDeletionResult delete(Long id) {
		Product product = requireProduct(id);
		ProductDeletionResult deletion = new ProductDeletionResult(
				product.getId(),
				Math.incrementExact(product.getVersion()),
				Instant.now());
		productRepository.delete(product);
		productRepository.flush();
		return deletion;
	}

	@Transactional
	public ProductCompositionResponse addComponent(Long productId, AddProductComponentRequest request) {
		topologyLock.acquire();
		Product parentProduct = requireProduct(productId);
		Product componentProduct = requireProduct(request.componentProductId());

		if (productCompositionRepository.existsByParentProductIdAndComponentProductId(
				parentProduct.getId(), componentProduct.getId())) {
			throw new DuplicateProductComponentException(componentProduct.getSku());
		}

		ProductComposition composition = parentProduct.addComponent(
				componentProduct,
				request.quantity(),
				request.measurementUnit(),
				cycleValidator);

		return toResponse(productCompositionRepository.saveAndFlush(composition));
	}

	@Transactional
	public ProductCompositionResponse updateComponent(
			Long productId,
			Long componentId,
			UpdateProductComponentRequest request) {
		Product parentProduct = requireProduct(productId);
		ProductComposition composition = parentProduct.updateComponent(
				componentId,
				request.quantity(),
				request.measurementUnit());
		return toResponse(productCompositionRepository.saveAndFlush(composition));
	}

	@Transactional
	public void removeComponent(Long productId, Long componentId) {
		Product parentProduct = requireProduct(productId);
		ProductComposition composition = parentProduct.removeComponent(componentId);
		productCompositionRepository.delete(composition);
		productCompositionRepository.flush();
	}

	public ProductCompositionTreeResponse getComposition(Long id) {
		Product product = requireProduct(id);
		CompositionGraph graph = loadReachableCompositionGraph(product.getId());
		return buildCompositionTree(product, graph);
	}

	private Product requireProduct(Long id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new ProductNotFoundException(id));
	}

	private List<ProductComposition> findDirectCompositions(Long productId) {
		return productCompositionRepository.findAllByParentProductId(productId);
	}

	private static ProductResponse toResponse(
			Product product,
			List<ProductComposition> compositions) {
		List<ProductCompositionResponse> components = compositions.stream()
				.map(ProductService::toResponse)
				.toList();

		return new ProductResponse(
				product.getId(),
				product.getName(),
				product.getDescription(),
				product.getSku(),
				product.getType(),
				product.getDefaultMeasurementUnit(),
				product.isActive(),
				product.getCreatedAt(),
				product.getUpdatedAt(),
				product.getVersion(),
				components);
	}

	private static ProductCompositionResponse toResponse(ProductComposition composition) {
		Product component = composition.getComponentProduct();
		return new ProductCompositionResponse(
				composition.getId(),
				component.getId(),
				component.getName(),
				component.getSku(),
				composition.getQuantity(),
				composition.getMeasurementUnit(),
				composition.getCreatedAt(),
				composition.getUpdatedAt());
	}

	private CompositionGraph loadReachableCompositionGraph(Long rootProductId) {
		Map<Long, List<CompositionEdge>> childrenByParentId = new HashMap<>();
		Set<Long> expandedProductIds = new HashSet<>();
		Set<Long> frontier = new LinkedHashSet<>();
		frontier.add(rootProductId);
		int depth = 0;
		int loadedNodeCount = 0;

		while (!frontier.isEmpty()) {
			List<Long> parentIds = frontier.stream()
					.filter(productId -> !expandedProductIds.contains(productId))
					.toList();
			if (parentIds.isEmpty()) {
				break;
			}

			List<ProductComposition> compositions = findCompositionsForParents(
					parentIds,
					MAX_COMPOSITION_TREE_NODES - loadedNodeCount);
			if (depth >= MAX_COMPOSITION_TREE_DEPTH && !compositions.isEmpty()) {
				throw compositionTreeDepthExceeded();
			}

			Set<Long> nextFrontier = new LinkedHashSet<>();
			for (ProductComposition composition : compositions) {
				Product parentProduct = composition.getParentProduct();
				Product componentProduct = composition.getComponentProduct();
				CompositionEdge edge = new CompositionEdge(
						composition.getId(),
						componentProduct.getId(),
						componentProduct.getName(),
						componentProduct.getSku(),
						composition.getQuantity(),
						composition.getMeasurementUnit());
				childrenByParentId.computeIfAbsent(parentProduct.getId(), ignored -> new ArrayList<>())
						.add(edge);

				loadedNodeCount++;
				if (loadedNodeCount > MAX_COMPOSITION_TREE_NODES) {
					throw compositionTreeNodeLimitExceeded();
				}
				if (!expandedProductIds.contains(componentProduct.getId())) {
					nextFrontier.add(componentProduct.getId());
				}
			}

			parentIds.forEach(expandedProductIds::add);
			frontier = nextFrontier;
			depth++;
		}

		Comparator<CompositionEdge> order = Comparator
				.comparing(CompositionEdge::componentProductId)
				.thenComparing(
						CompositionEdge::compositionId,
						Comparator.nullsLast(Comparator.naturalOrder()));
		childrenByParentId.values().forEach(children -> children.sort(order));
		return new CompositionGraph(childrenByParentId);
	}

	private List<ProductComposition> findCompositionsForParents(
			List<Long> parentIds,
			int remainingNodeBudget) {
		List<ProductComposition> result = new ArrayList<>();
		for (int start = 0; start < parentIds.size(); start += COMPOSITION_FETCH_BATCH_SIZE) {
			int end = Math.min(start + COMPOSITION_FETCH_BATCH_SIZE, parentIds.size());
			List<Long> parentBatch = parentIds.subList(start, end);
			int pageNumber = 0;
			List<ProductComposition> page;
			do {
				page = productCompositionRepository.findAllByParentProductIdIn(
						parentBatch,
						PageRequest.of(
								pageNumber++,
								COMPOSITION_RESULT_PAGE_SIZE,
								Sort.by("id").ascending()));
				result.addAll(page);
				if (result.size() > remainingNodeBudget) {
					throw compositionTreeNodeLimitExceeded();
				}
			} while (page.size() == COMPOSITION_RESULT_PAGE_SIZE);
		}
		return result;
	}

	private static ProductCompositionTreeResponse buildCompositionTree(
			Product rootProduct,
			CompositionGraph graph) {
		Deque<TreeFrame> pending = new ArrayDeque<>();
		Set<Long> path = new HashSet<>();
		pending.addLast(TreeFrame.root(rootProduct, graph.childrenOf(rootProduct.getId())));
		path.add(rootProduct.getId());
		List<ProductCompositionTreeResponse.Component> rootComponents = List.of();
		int renderedNodeCount = 0;

		while (!pending.isEmpty()) {
			TreeFrame frame = pending.peekLast();
			if (frame.hasNextChild()) {
				CompositionEdge edge = frame.nextChild();
				int childDepth = frame.depth() + 1;
				if (childDepth > MAX_COMPOSITION_TREE_DEPTH) {
					throw compositionTreeDepthExceeded();
				}
				renderedNodeCount++;
				if (renderedNodeCount > MAX_COMPOSITION_TREE_NODES) {
					throw compositionTreeNodeLimitExceeded();
				}
				if (!path.add(edge.componentProductId())) {
					throw new ProductCompositionCycleException(
							frame.productSku(),
							edge.componentSku());
				}
				pending.addLast(TreeFrame.component(
						edge,
						childDepth,
						graph.childrenOf(edge.componentProductId())));
				continue;
			}

			pending.removeLast();
			path.remove(frame.productId());
			List<ProductCompositionTreeResponse.Component> children = frame.builtChildren();
			if (frame.incomingEdge() == null) {
				rootComponents = children;
				continue;
			}

			CompositionEdge edge = frame.incomingEdge();
			ProductCompositionTreeResponse.Component component =
					new ProductCompositionTreeResponse.Component(
							edge.compositionId(),
							edge.componentProductId(),
							edge.componentName(),
							edge.componentSku(),
							edge.quantity(),
							edge.measurementUnit(),
							children);
			pending.peekLast().addBuiltChild(component);
		}

		return new ProductCompositionTreeResponse(
				rootProduct.getId(),
				rootProduct.getName(),
				rootProduct.getSku(),
				rootProduct.getType(),
				rootProduct.getDefaultMeasurementUnit(),
				rootComponents);
	}

	private static InvalidProductCompositionException compositionTreeDepthExceeded() {
		return new InvalidProductCompositionException(
				"Composition tree exceeds the maximum depth of "
						+ MAX_COMPOSITION_TREE_DEPTH);
	}

	private static InvalidProductCompositionException compositionTreeNodeLimitExceeded() {
		return new InvalidProductCompositionException(
				"Composition tree exceeds the maximum node count of "
						+ MAX_COMPOSITION_TREE_NODES);
	}

	private record CompositionGraph(Map<Long, List<CompositionEdge>> childrenByParentId) {

		private CompositionGraph {
			Map<Long, List<CompositionEdge>> immutableChildren = new HashMap<>();
			childrenByParentId.forEach((productId, children) ->
					immutableChildren.put(productId, List.copyOf(children)));
			childrenByParentId = Map.copyOf(immutableChildren);
		}

		List<CompositionEdge> childrenOf(Long productId) {
			return childrenByParentId.getOrDefault(productId, List.of());
		}
	}

	private record CompositionEdge(
			Long compositionId,
			Long componentProductId,
			String componentName,
			String componentSku,
			BigDecimal quantity,
			MeasurementUnit measurementUnit) {
	}

	private static final class TreeFrame {

		private final Long productId;
		private final String productSku;
		private final CompositionEdge incomingEdge;
		private final int depth;
		private final List<CompositionEdge> children;
		private final List<ProductCompositionTreeResponse.Component> builtChildren = new ArrayList<>();
		private int nextChildIndex;

		private TreeFrame(
				Long productId,
				String productSku,
				CompositionEdge incomingEdge,
				int depth,
				List<CompositionEdge> children) {
			this.productId = productId;
			this.productSku = productSku;
			this.incomingEdge = incomingEdge;
			this.depth = depth;
			this.children = children;
		}

		static TreeFrame root(Product product, List<CompositionEdge> children) {
			return new TreeFrame(product.getId(), product.getSku(), null, 0, children);
		}

		static TreeFrame component(
				CompositionEdge edge,
				int depth,
				List<CompositionEdge> children) {
			return new TreeFrame(
					edge.componentProductId(),
					edge.componentSku(),
					edge,
					depth,
					children);
		}

		boolean hasNextChild() {
			return nextChildIndex < children.size();
		}

		CompositionEdge nextChild() {
			return children.get(nextChildIndex++);
		}

		void addBuiltChild(ProductCompositionTreeResponse.Component component) {
			builtChildren.add(component);
		}

		Long productId() {
			return productId;
		}

		String productSku() {
			return productSku;
		}

		CompositionEdge incomingEdge() {
			return incomingEdge;
		}

		int depth() {
			return depth;
		}

		List<ProductCompositionTreeResponse.Component> builtChildren() {
			return List.copyOf(builtChildren);
		}
	}
}
