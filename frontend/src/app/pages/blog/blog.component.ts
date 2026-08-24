import { Component } from '@angular/core';
import { NgFor, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface Article {
  category: string;
  categoryColor: string;
  date: string;
  title: string;
  description: string;
  image: string;
  url: string;
}

interface QuickCard {
  icon: string;
  label: string;
  title: string;
  color: string;
  url: string;
}

@Component({
  selector: 'app-blog',
  standalone: true,
  imports: [NgFor, NgClass, FormsModule],  //Formulario de buscador agregado
  templateUrl: './blog.component.html',
  styleUrls: ['./blog.component.css']
})
export class BlogComponent {
  filters = ['Todos los temas', 'Piel sensible', 'Consejos', 'Expertos', 'Piel madura', 'Cuidado solar', 'Ingredientes'];
  activeFilter = 'Todos los temas';

  quickCards: QuickCard[] = [
    { icon: '🛡️', label: 'PROTECCIÓN', title: 'SPF todos los días', color: 'card-blue', url: 'https://www.nivea.es/consejos/proteccion-solar/que-es-spf' },
    { icon: '💧', label: 'HIDRATACIÓN', title: 'Texturas ligeras para la mañana', color: 'card-light', url: 'https://dermotienda.ec/blogs/dermocosmetica/hidratacion-ligera-verano' },
    { icon: '⭐', label: 'RECOMENDADO', title: 'Rutinas simples y constantes', color: 'card-orange', url: 'https://www.healthline.com/health/beauty-skin-care/order-of-skin-care' },
  ];

  articles: Article[] = [
    {
      category: 'GUÍA',
      categoryColor: 'tag-blue',
      date: 'ABRIL 2026',
      title: 'Rutina facial rápida para días ocupados',
      description: 'Tres pasos claros para limpiar, hidratar y proteger la piel sin perder tiempo por la mañana.',
      image: 'https://images.unsplash.com/photo-1556228720-195a672e8a03?w=400&q=80',
      url: 'https://www.maquillalia.com/blog/skincare-express-la-mejor-rutina-en-pasos-para-mananas-con-prisa-b-247.html'
    },
    {
      category: 'CONSEJOS',
      categoryColor: 'tag-orange',
      date: 'MARZO 2026',
      title: 'Cómo elegir una crema que se adapte a tu piel',
      description: 'Qué revisar en la textura, los activos y el nivel de hidratación que tu piel necesita.',
      image: 'https://zentcare.com/cdn/shop/articles/pexels-shvets-production-9774606_e50ff2db-616b-4005-9323-627c8183c19e.jpg?v=1778957261&width=1080',
      url: 'https://zentcare.com/es/blogs/noticias/como-elegir-una-crema-para-la-piel'
    },
    {
      category: 'CUIDADO SOLAR',
      categoryColor: 'tag-gray',
      date: 'FEBRERO 2026',
      title: 'Protección solar durante todo el año',
      description: 'Una explicación simple para no abandonar el protector cuando baja la temperatura.',
      image: 'https://mmo.aiircdn.com/766/697ce5057d4af.jpg',
      url: 'https://www.fda.gov/consumers/articulos-para-el-consumidor-en-espanol/consejos-para-mantenerse-seguro-bajo-el-sol-desde-la-proteccion-solar-hasta-las-gafas-de-sol'
    },
    {
      category: 'INGREDIENTES',
      categoryColor: 'tag-blue',
      date: 'MAYO 2026',
      title: 'Niacinamida: el activo que tu piel estaba esperando',
      description: 'Regula el sebo, cierra los poros y unifica el tono. Descubre cómo incorporarla sin irritar la piel.',
      image: 'https://www.laroche-posay.es/-/media/project/loreal/brand-sites/lrp/emea/es/articles/anti-age/niacinamida-y-para-que-sirve-v2_1.jpg?cx=0.58&amp;ch=600&amp;cy=0.43&amp;cw=2000&hash=C4AD897652AE06C75EE5E4DC17D8CE50',
      url: 'http://laroche-posay.es/article/que-es-la-niacinamida-y-cuales-son-sus-beneficios'
    },
    {
      category: 'GUÍA',
      categoryColor: 'tag-blue',
      date: 'MAYO 2026',
      title: 'Cómo leer la etiqueta de un producto cosmético',
      description: 'INCI, orden de ingredientes y qué señales indican que un producto es realmente efectivo.',
      image: 'https://cdn.shopify.com/s/files/1/0921/2752/7249/files/Inci-3.jpg',
      url: 'https://www.saper.es/blogs/blog/inci-cosmetica'
    },
    {
      category: 'CONSEJOS',
      categoryColor: 'tag-orange',
      date: 'ABRIL 2026',
      title: 'Piel sensible: errores comunes y cómo evitarlos',
      description: 'Desde el agua muy caliente hasta mezclar demasiados activos: pequeños cambios que hacen gran diferencia.',
      image: 'https://images.unsplash.com/photo-1616394584738-fc6e612e71b9?w=400&q=80',
      url: 'https://www.laroche-posay.es/article/causas-de-la-piel-sensible-factores-internos-y-externos'
    },
    {
      category: 'EXPERTOS',
      categoryColor: 'tag-gray',
      date: 'ABRIL 2026',
      title: 'Retinol: guía completa para principiantes',
      description: 'Frecuencia, concentración y cómo combinarlo sin provocar irritación ni pelarse en el intento.',
      image: 'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?w=400&q=80',
      url: 'https://www.skinceuticals.es/skin-c-mag/guia-cantidad-y-frecuencia-uso-retinol.html'
    },
    {
      category: 'INGREDIENTES',
      categoryColor: 'tag-blue',
      date: 'MARZO 2026',
      title: 'Vitamina C: aliada del tono uniforme y el antienvejecimiento',
      description: 'Qué forma de vitamina C buscar, cómo almacenarla correctamente y con qué activos combinarla.',
      image: 'https://www.isdin.com/es/blog/wp-content/uploads/2025/04/2025_03_VitaminC_IMG05-1.jpg',
      url: 'https://www.isdin.com/es/blog/vitamina-c-que-es-para-que-sirve-y-como-usarla/'
    },
    {
      category: 'CONSEJOS',
      categoryColor: 'tag-orange',
      date: 'MARZO 2026',
      title: 'Doble limpieza: ¿realmente la necesitas?',
      description: 'Cuándo vale la pena hacer dos pasos de limpieza y cuándo es suficiente con uno solo.',
      image: 'https://images.unsplash.com/photo-1556228578-0d85b1a4d571?w=400&q=80',
      url: 'https://www.nivea.es/consejos/piel-bonita/doble-limpieza-facial'
    },
    {
      category: 'GUÍA',
      categoryColor: 'tag-blue',
      date: 'FEBRERO 2026',
      title: 'El orden correcto para aplicar tu skincare',
      description: 'Sérum antes de crema, tónico antes de sérum: la secuencia exacta para maximizar la absorción.',
      image: 'https://www.isdin.com/es/blog/wp-content/uploads/2024/03/2024_Rutina_IMG04-900x464.png',
      url: 'https://www.isdin.com/es/blog/en-que-orden-se-aplican-las-cremas/'
    },
    {
      category: 'EXPERTOS',
      categoryColor: 'tag-gray',
      date: 'ENERO 2026',
      title: 'Hidratación vs. Humectación: no son lo mismo',
      description: 'La diferencia entre captar agua y retenerla, y por qué ambas funciones son esenciales en tu rutina.',
      image: 'https://img.nivea.com/-/media/nivea/local/ar/2021/articulos/diferencia-entre-hidratar-y-humectar/hidratar-humectar_1.webp?mw=1180&hash=C446CF5B7DFB761CBA7FE786E2AB6378',
      url: 'https://www.nivea.com.ar/consejos/diferencia-entre-hidratar-y-humectar'
    },
    {
      category: 'CONSEJOS',
      categoryColor: 'tag-orange',
      date: 'ENERO 2026',
      title: 'Cuidado de la piel madura: por dónde empezar',
      description: 'Activos clave como péptidos, retinoides y antioxidantes para una piel que luce firme y luminosa.',
      image: 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTW4A4tG0hSV6Uez9oSl1YBa4P1-ktPm3X4Xg&s',
      url: 'https://www.consalud.es/estetic/cosmetica/el-retinal-se-convierte-en-el-activo-perfecto-para-rejuvenecer-la-piel-a-partir-de-los-40-anos.html'
    },
    {
      category: 'PIEL SENSIBLE',
      categoryColor: 'tag-blue',
      date: 'MAYO 2026',
      title: 'Barreras de protección: cómo fortalecer la piel sensible',
      description: 'La barrera cutánea es tu primera línea de defensa. Aprende qué ingredientes la refuerzan y cuáles la debilitan.',
      image: 'https://img.nivea.com/-/media/miscellaneous/media-center-items/2/5/e/d697f37e6beb4f9da460da73487864b7-original.png?rx=0&ry=163&rw=1180&rh=460&mw=1024&hash=A2DE07881F02E98FB0677B25C87B09DB',
      url: 'https://www.nivea.es/consejos/piel-bonita/barrera-cutanea-danada'
    },
    {
      category: 'PIEL SENSIBLE',
      categoryColor: 'tag-orange',
      date: 'ABRIL 2026',
      title: 'Ingredientes que debes evitar si tienes piel reactiva',
      description: 'Alcohol, fragancias artificiales y ciertos conservantes: conoce qué buscar en la etiqueta antes de comprar.',
      image: 'https://www.elconfidencialdigital.com/asset/thumbnail,1920,1080,center,center/media/elconfidencialdigital/images/2026/03/23/2026032313261226713.jpg',
      url: 'https://www.elconfidencialdigital.com/articulo/cuidate/ingredientes-que-debes-evitar-maquillaje-natural-tienes-piel-sensible/202603231326361011211.html'
    },
    {
      category: 'PIEL MADURA',
      categoryColor: 'tag-gray',
      date: 'MAYO 2026',
      title: 'Péptidos y ácido hialurónico: dúo clave para la piel madura',
      description: 'Cómo estos dos activos trabajan juntos para recuperar firmeza, densidad y luminosidad con el paso del tiempo.',
      image: 'https://media.glamour.mx/photos/696598a3ca28be92751ea9c8/16:9/w_1920,c_limit/piel-hailey-bieber.jpg',
      url: 'https://www.glamour.mx/articulos/peptidos-la-ciencia-detras-de-la-piel-firme-y-luminosa-en-2026'
    },
  ];
  // Metodo de busqueda implementado
  searchQuery = '';

  get filteredArticles(): Article[] {
    const query = this.searchQuery.toLowerCase().trim();

    // Artículos extra por filtro (por título exacto)
    const extraByFilter: Record<string, string[]> = {
      'Piel sensible': ['Piel sensible: errores comunes y cómo evitarlos'],
      'Piel madura': ['Cuidado de la piel madura: por dónde empezar'],
      'Ingredientes': [
        'Cómo leer la etiqueta de un producto cosmético',
        'Ingredientes que debes evitar si tienes piel reactiva'
      ],
    };

    // Categorías principales por filtro
    const filterMap: Record<string, string[]> = {
      'Piel sensible': ['PIEL SENSIBLE'],
      'Consejos': ['CONSEJOS'],
      'Expertos': ['EXPERTOS'],
      'Piel madura': ['PIEL MADURA'],
      'Cuidado solar': ['CUIDADO SOLAR'],
      'Ingredientes': ['INGREDIENTES'],
      'Guía': ['GUÍA'],
    };

    let result = this.articles;

    if (this.activeFilter !== 'Todos los temas') {
      const allowedCategories = filterMap[this.activeFilter] ?? [];
      const extraTitles = extraByFilter[this.activeFilter] ?? [];

      result = result.filter(art =>
        allowedCategories.includes(art.category.toUpperCase()) ||
        extraTitles.includes(art.title)
      );
    }

    if (query) {
      result = result.filter(art =>
        art.title.toLowerCase().includes(query) ||
        art.description.toLowerCase().includes(query) ||
        art.category.toLowerCase().includes(query)
      );
    }

    return result;
  }

  setFilter(filter: string) {
    this.activeFilter = filter;
  }
  openArticle(url: string) {
    window.open(url, '_blank');
  }
}